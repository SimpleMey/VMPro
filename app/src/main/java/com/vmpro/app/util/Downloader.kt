package com.vmpro.app.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vmpro.app.BuildConfig
import java.io.File

/** Lightweight system actions (open URL, uninstall). Downloads go through DownloadController. */
object Downloader {

    /** Open a URL in the browser. */
    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Share VMPro's own APK file via the system share sheet (WhatsApp/Telegram/etc. send
     * it as an installable attachment). Copies the installed APK to a cache dir and exposes
     * it through the app's FileProvider.
     */
    fun shareApk(context: Context) {
        runCatching {
            val src = File(context.applicationInfo.sourceDir)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val dest = File(dir, "VMPro-${BuildConfig.VERSION_NAME}.apk")
            src.copyTo(dest, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "VMPro — get it at https://vmpro.app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(send, "Share VMPro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "Couldn't share the app", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open a Telegram channel in the Telegram app if installed (tg:// deep link),
     * otherwise fall back to the web (t.me) in the browser.
     */
    fun openTelegram(context: Context, channel: String) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$channel"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            openUrl(context, "https://t.me/$channel")
        }
    }

    /**
     * Launch the system uninstall dialog for [packageName].
     *
     * Primary path is [android.content.pm.PackageInstaller.uninstall] (the modern, reliable
     * API; needs REQUEST_DELETE_PACKAGES). It reports back to [UninstallReceiver], which
     * shows the confirmation dialog. Falls back to the legacy ACTION_DELETE intent.
     */
    fun uninstall(context: Context, packageName: String) {
        val app = context.applicationContext
        try {
            val installer = app.packageManager.packageInstaller
            val statusIntent = Intent(app, UninstallReceiver::class.java)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            val pending = PendingIntent.getBroadcast(
                app, packageName.hashCode(), statusIntent, flags,
            )
            installer.uninstall(packageName, pending.intentSender)
            return
        } catch (_: Exception) {
            // fall through to the legacy intent
        }
        val deleteIntent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(deleteIntent) }.onFailure {
            Toast.makeText(context, "Couldn't open the uninstaller", Toast.LENGTH_SHORT).show()
        }
    }
}
