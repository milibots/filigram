package com.filigram.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Base urls, headers, cookies and tokens for every content source, served from the same
 * three tiers as the rest of the remote config. Anything missing falls back to the value
 * each API object ships with, so the app still works with every server unreachable.
 */
object SourceConfig {

    private const val TAG = "SourceConfig"

    private const val CF_URL = "https://filigramv1.miladjobs22.workers.dev/api/sources?all=1"
    private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/milibots/filigram_config/refs/heads/main/data.json"
    private const val ARVAN_URL = "https://filmapi1.milaadfarzian-tnljt.arvanedge.ir/api/sources?all=1"

    private const val PREFS_NAME = "filigram_source_config"
    private const val KEY_CACHED = "cached_sources_json"

    data class SourceEntry(
        val key: String,
        val label: String,
        val planet: String,
        val enabled: Boolean,
        val order: Int,
        val baseUrl: String,
        val headers: Map<String, String>,
        val cookies: String,
        val auth: Map<String, String>,
        val imageRewrite: List<Pair<String, String>>
    )

    private val entries = ConcurrentHashMap<String, SourceEntry>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.getString(KEY_CACHED, null)?.let { cached ->
            runCatching { adopt(JSONArray(cached)) }
        }

        val fresh = fetchFirstAvailable()
        if (fresh != null) {
            adopt(fresh)
            prefs.edit().putString(KEY_CACHED, fresh.toString()).apply()
            AppLogger.s(TAG, "تنظیمات منابع از سرور دریافت شد (${entries.size} سیاره).")
        } else {
            AppLogger.w(TAG, "تنظیمات منابع از هیچ سروری دریافت نشد؛ مقادیر داخلی برنامه استفاده می‌شود.")
        }
    }

    private suspend fun fetchFirstAvailable(): JSONArray? {
        executeGet(CF_URL, 3000L)?.let { raw ->
            runCatching { JSONArray(raw) }.getOrNull()?.takeIf { it.length() > 0 }?.let { return it }
        }
        executeGet(GITHUB_RAW_URL, 3000L)?.let { raw ->
            runCatching { JSONObject(raw).optJSONArray("sources") }.getOrNull()
                ?.takeIf { it.length() > 0 }?.let { return it }
        }
        executeGet(ARVAN_URL, 5000L)?.let { raw ->
            runCatching { JSONArray(raw) }.getOrNull()?.takeIf { it.length() > 0 }?.let { return it }
        }
        return null
    }

    private suspend fun executeGet(url: String, timeoutMs: Long): String? = withTimeoutOrNull(timeoutMs) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Filigram-Android-Client/1.2")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun adopt(array: JSONArray) {
        val parsed = mutableMapOf<String, SourceEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val key = obj.optString("key").takeIf { it.isNotBlank() } ?: continue
            parsed[key] = SourceEntry(
                key = key,
                label = obj.optString("label", key),
                planet = obj.optString("planet", key),
                enabled = obj.optBoolean("enabled", true),
                order = obj.optInt("order", i + 1),
                baseUrl = obj.optString("base_url", "").trimEnd('/'),
                headers = toStringMap(obj.optJSONObject("headers")),
                cookies = obj.optString("cookies", ""),
                auth = toStringMap(obj.optJSONObject("auth")),
                imageRewrite = toRewriteRules(obj.optJSONArray("image_rewrite"))
            )
        }
        if (parsed.isNotEmpty()) {
            entries.clear()
            entries.putAll(parsed)
        }
    }

    private fun toStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val value = obj.optString(k, "")
            if (value.isNotBlank()) map[k] = value
        }
        return map
    }

    private fun toRewriteRules(array: JSONArray?): List<Pair<String, String>> {
        if (array == null) return emptyList()
        val rules = mutableListOf<Pair<String, String>>()
        for (i in 0 until array.length()) {
            val rule = array.optJSONArray(i) ?: continue
            if (rule.length() == 2) rules.add(rule.optString(0) to rule.optString(1))
        }
        return rules
    }

    fun baseUrl(key: String, fallback: String): String =
        entries[key]?.baseUrl?.takeIf { it.isNotBlank() } ?: fallback

    fun headers(key: String, fallback: Map<String, String> = emptyMap()): Map<String, String> {
        val entry = entries[key] ?: return fallback
        val merged = fallback.toMutableMap()
        merged.putAll(entry.headers)
        if (entry.cookies.isNotBlank()) merged["Cookie"] = entry.cookies
        return merged
    }

    fun auth(key: String): Map<String, String> = entries[key]?.auth ?: emptyMap()

    fun rewriteImage(key: String, url: String): String {
        val rules = entries[key]?.imageRewrite ?: return url
        var result = url
        for ((from, to) in rules) {
            if (from.isNotBlank()) result = result.replace(from, to)
        }
        return result
    }

    fun label(key: String, fallback: String): String = entries[key]?.label ?: fallback

    fun isEnabled(key: String): Boolean = entries[key]?.enabled ?: true

    fun enabledKeys(): List<String> =
        entries.values.filter { it.enabled }.sortedBy { it.order }.map { it.key }
}
