package com.vmpro.app.util

import android.os.ParcelFileDescriptor
import com.vmpro.app.IShizukuUserService
import java.io.FileInputStream
import kotlin.system.exitProcess

/**
 * Runs inside the separate process Shizuku spawns with shell (or root) privileges, so `pm install`
 * here installs without the system prompt. It must not touch app Context or resources: it is a
 * different process. The APK is streamed in over a file descriptor the app opened, so no
 * shell-readable file path is required.
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun install(apk: ParcelFileDescriptor, size: Long): String {
        return try {
            val process = Runtime.getRuntime()
                .exec(arrayOf("pm", "install", "-r", "-S", size.toString()))
            FileInputStream(apk.fileDescriptor).use { input ->
                process.outputStream.use { out -> input.copyTo(out) }
            }
            val code = process.waitFor()
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            if (code == 0 && out.contains("Success", ignoreCase = true)) {
                "OK"
            } else {
                "ERR:" + err.ifBlank { out }.ifBlank { "pm install exited with code $code" }.trim()
            }
        } catch (e: Throwable) {
            "ERR:" + (e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { apk.close() }
        }
    }
}
