package com.vmpro.app.util

import android.app.DownloadManager
import android.content.Context
import androidx.core.content.getSystemService

/** A finished download VMPro created, read back from the system [DownloadManager]. */
data class DownloadedFile(val id: Long, val name: String, val bytes: Long)

/**
 * Lists and deletes the files VMPro downloaded. [DownloadManager] scopes both its queries and
 * [DownloadManager.remove] to the calling app, so this only ever sees (and deletes) VMPro's own
 * downloads, and `remove` reliably deletes the file even under scoped storage.
 */
object DownloadsRepo {

    /** Successful VMPro downloads, newest first. */
    fun list(context: Context): List<DownloadedFile> {
        val dm = context.getSystemService<DownloadManager>() ?: return emptyList()
        val out = ArrayList<DownloadedFile>()
        runCatching {
            dm.query(DownloadManager.Query()).use { c ->
                if (c == null) return emptyList()
                val iId = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val iTitle = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val iStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val iBytes = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    if (iId < 0) continue
                    if (iStatus >= 0 && c.getInt(iStatus) != DownloadManager.STATUS_SUCCESSFUL) continue
                    val name = (if (iTitle >= 0) c.getString(iTitle) else null).orEmpty()
                    val bytes = if (iBytes >= 0) c.getLong(iBytes) else 0L
                    out.add(DownloadedFile(c.getLong(iId), name.ifBlank { "download" }, bytes))
                }
            }
        }
        return out.asReversed()
    }

    /** Delete one download (removes the file and its record). */
    fun delete(context: Context, id: Long) {
        context.getSystemService<DownloadManager>()?.let { runCatching { it.remove(id) } }
    }

    /** Delete every VMPro download. */
    fun clearAll(context: Context) {
        val dm = context.getSystemService<DownloadManager>() ?: return
        val ids = list(context).map { it.id }.toLongArray()
        if (ids.isNotEmpty()) runCatching { dm.remove(*ids) }
    }
}
