package com.filigram.cinema

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object AppCacheManager {

    private const val PREFS_NAME = "filigram_api_cache"
    private var prefs: SharedPreferences? = null
    private var cacheDir: File? = null

    const val TTL_DETAILS = 7 * 24 * 3600 * 1000L // 7 days for movie details (title, poster, description)
    const val TTL_DISCOVERY = 4 * 3600 * 1000L    // 4 hours for home/feed listings
    // Download & streaming links are NEVER cached — always fetched fresh to avoid stale/broken links.
    // Do NOT add /link, /downloads/, or link-info-request endpoints to any isCacheable condition.

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dir = File(context.filesDir, "smart_api_cache")
        if (!dir.exists()) dir.mkdirs()
        cacheDir = dir
    }

    fun put(key: String, data: String) {
        try {
            val sanitized = key.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val file = File(cacheDir ?: return, sanitized)
            file.writeText(data)
            prefs?.edit()?.putLong("time_$sanitized", System.currentTimeMillis())?.apply()
        } catch (_: Exception) {}
    }

    fun get(key: String, maxAgeMillis: Long = TTL_DETAILS): String? {
        val sanitized = key.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val savedTime = prefs?.getLong("time_$sanitized", 0L) ?: 0L
        if (savedTime == 0L) return null
        if (maxAgeMillis > 0 && System.currentTimeMillis() - savedTime > maxAgeMillis) return null
        val file = File(cacheDir ?: return null, sanitized)
        return if (file.exists()) file.readText() else null
    }

    fun hasValid(key: String, maxAgeMillis: Long = TTL_DETAILS): Boolean {
        return get(key, maxAgeMillis) != null
    }

    fun clear() {
        try {
            cacheDir?.deleteRecursively()
            prefs?.edit()?.clear()?.apply()
            cacheDir?.mkdirs()
        } catch (_: Exception) {}
    }

    fun getCacheSizeBytes(context: Context): Long {
        var totalSize = 0L
        try {
            totalSize += getDirSize(context.cacheDir)
            totalSize += getDirSize(context.codeCacheDir)
        } catch (_: Exception) {}
        return totalSize
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "۰ مگابایت"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f گیگابایت", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f مگابایت", mb)
            else -> String.format(java.util.Locale.US, "%.0f کیلوبایت", kb)
        }
    }

    fun clearAll(context: Context) {
        clear()
        try {
            context.cacheDir?.deleteRecursively()
            val dir = File(context.cacheDir, "api_responses")
            if (!dir.exists()) dir.mkdirs()
            cacheDir = dir
        } catch (_: Exception) {}
    }
}
