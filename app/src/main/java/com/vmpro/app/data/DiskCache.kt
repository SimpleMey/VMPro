package com.vmpro.app.data

import java.io.File

/**
 * A tiny file-backed cache of the last-known API responses (raw JSON strings), so the Apps tab
 * can paint instantly from the previous launch's data while the network refresh runs in the
 * background. Best-effort: every read/write swallows IO errors and the app falls back to the
 * network, so a missing or unreadable cache never breaks a load.
 */
class DiskCache(dir: File) {
    private val root = File(dir, "api-cache").also { runCatching { it.mkdirs() } }

    private fun fileFor(key: String) =
        File(root, key.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    fun read(key: String): String? =
        runCatching { fileFor(key).takeIf { it.exists() }?.readText() }.getOrNull()

    fun write(key: String, value: String) {
        runCatching { fileFor(key).writeText(value) }
    }
}
