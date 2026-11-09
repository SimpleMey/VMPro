package com.vmpro.app.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import com.vmpro.app.BuildConfig
import com.vmpro.app.IShizukuUserService
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Optional silent (no-prompt) APK install through Shizuku, for users who run it. Everything here
 * degrades gracefully: when [available] is false, callers fall back to the normal system installer.
 * Installs run in a Shizuku user service (shell/root privileges); the APK is streamed to that
 * service over a file descriptor, so no shell-readable file path is needed.
 */
object ShizukuInstaller {
    const val PERMISSION_REQUEST_CODE = 7001

    /** Shizuku is installed, running, and reachable. */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Shizuku is running and has granted us access. */
    fun hasPermission(): Boolean = runCatching {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** True when we can silently install right now. */
    fun available(): Boolean = hasPermission()

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /** Install the APK at [uri] via the Shizuku user service. Returns (success, message). */
    suspend fun install(context: Context, uri: Uri): Pair<Boolean, String> {
        val resolver = context.contentResolver
        val size = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }
            .getOrNull() ?: -1L
        if (size <= 0L) return false to "Could not read the downloaded APK."
        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
            ?: return false to "Could not open the downloaded APK."

        return suspendCancellableCoroutine { cont ->
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuUserService::class.java.name)
            ).daemon(false).processNameSuffix("installer").version(BuildConfig.VERSION_CODE)

            lateinit var connection: ServiceConnection
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    // Binder calls are blocking, so do the install off the main thread.
                    thread {
                        val result = try {
                            if (binder == null || !binder.pingBinder())
                                "ERR:Shizuku service unavailable"
                            else IShizukuUserService.Stub.asInterface(binder).install(pfd, size)
                        } catch (e: Throwable) {
                            "ERR:" + (e.message ?: e.javaClass.simpleName)
                        }
                        runCatching { pfd.close() }
                        runCatching { Shizuku.unbindUserService(args, connection, true) }
                        if (cont.isActive) {
                            if (result == "OK") cont.resume(true to "Installed.")
                            else cont.resume(false to result.removePrefix("ERR:"))
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: Throwable) {
                runCatching { pfd.close() }
                if (cont.isActive) {
                    cont.resume(false to (e.message ?: "Could not start the Shizuku service"))
                }
            }
        }
    }
}
