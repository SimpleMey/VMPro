package com.vmpro.app.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

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
