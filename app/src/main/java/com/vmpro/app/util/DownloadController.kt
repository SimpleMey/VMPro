package com.vmpro.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.vmpro.app.analytics.Analytics
import com.vmpro.app.data.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DownloadPhase { IDLE, DOWNLOADING, DONE, FAILED }

/**
 * Wraps the system [DownloadManager]: enqueues asset downloads, tracks their phase per
 * asset URL, and installs finished APKs. State is in-memory for the app session.
 */
class DownloadController(context: Context) {

    private val appContext = context.applicationContext
    private val dm = appContext.getSystemService<DownloadManager>()!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _phases = MutableStateFlow<Map<String, DownloadPhase>>(emptyMap())
    val phases: StateFlow<Map<String, DownloadPhase>> = _phases

    /** Download progress percent (0..100) per asset URL, while downloading. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    private val idToAsset = mutableMapOf<Long, Asset>()
    private val urlToLocalUri = mutableMapOf<String, Uri>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val asset = idToAsset[id] ?: return
            onFinished(id, asset)
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun unregister() {
        runCatching { appContext.unregisterReceiver(receiver) }
        scope.cancel()
    }

    fun phaseOf(asset: Asset): DownloadPhase =
        _phases.value[asset.downloadUrl] ?: DownloadPhase.IDLE

    fun download(asset: Asset) {
        if (phaseOf(asset) == DownloadPhase.DOWNLOADING) return
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl)).apply {
            setTitle(asset.name)
            setDescription("VMPro")
            setMimeType(
                if (asset.isApk) "application/vnd.android.package-archive" else "application/zip"
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val id = dm.enqueue(request)
        idToAsset[id] = asset
        setPhase(asset.downloadUrl, DownloadPhase.DOWNLOADING)
        setProgress(asset.downloadUrl, 0)
        pollProgress(id, asset.downloadUrl)
        Analytics.downloadStarted(asset.name, if (asset.isApk) "apk" else "module")
        Toast.makeText(appContext, "Downloading ${asset.name}…", Toast.LENGTH_SHORT).show()
    }

    /** Poll DownloadManager for byte progress and publish a 0..100 percent until it ends. */
    private fun pollProgress(id: Long, url: String) {
        scope.launch {
            while (isActive) {
                var finished = false
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (c == null || !c.moveToFirst()) {
                        finished = true
                        return@use
                    }
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = c.getLong(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = c.getLong(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    if (total > 0) {
                        setProgress(url, ((soFar * 100) / total).toInt().coerceIn(0, 100))
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL ||
                        status == DownloadManager.STATUS_FAILED
                    ) {
                        finished = true
                    }
                }
                if (finished) break
                delay(400)
            }
        }
    }

    private fun onFinished(id: Long, asset: Asset) {
        val url = asset.downloadUrl
        val query = DownloadManager.Query().setFilterById(id)
        dm.query(query).use { c ->
            if (c == null || !c.moveToFirst()) {
                clearProgress(url)
                setPhase(url, DownloadPhase.FAILED)
                Analytics.downloadFailed(asset.name)
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            clearProgress(url)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                urlToLocalUri[url] = dm.getUriForDownloadedFile(id)
                setPhase(url, DownloadPhase.DONE)
                Analytics.downloadCompleted(asset.name)
            } else {
                setPhase(url, DownloadPhase.FAILED)
                Analytics.downloadFailed(asset.name)
                Toast.makeText(appContext, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Launch the package installer for a finished APK. */
    fun install(asset: Asset) {
        val uri = urlToLocalUri[asset.downloadUrl] ?: run {
            Toast.makeText(appContext, "File not found — download again", Toast.LENGTH_SHORT).show()
            setPhase(asset.downloadUrl, DownloadPhase.IDLE)
            return
        }
        Analytics.installClicked(asset.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }.onFailure {
            Toast.makeText(appContext, "Could not open installer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setPhase(url: String, phase: DownloadPhase) {
        _phases.update { it + (url to phase) }
    }

    private fun setProgress(url: String, percent: Int) {
        _progress.update { it + (url to percent) }
    }

    private fun clearProgress(url: String) {
        _progress.update { it - url }
    }
}
