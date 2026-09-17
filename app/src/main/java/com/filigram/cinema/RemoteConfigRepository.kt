package com.filigram.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class AppBanner(
    val id: String,
    val imageUrl: String,
    val targetUrl: String? = null,
    val order: Int = 0,
    val title: String? = null
) {
    fun toMovieItem(): MovieItem = MovieItem(
        id = id.hashCode(),
        title = title ?: "ویژه فیلیگرام",
        image = imageUrl,
        type = 0,
        slug = targetUrl
    )
}

object RemoteConfigRepository {

    private const val TAG = "RemoteConfigRepo"

    // ─── 3 Remote Data Sources ───────────────────────────────────────────
    private const val CF_BASE_URL = "https://filigramv1.miladjobs22.workers.dev"
    private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/milibots/filigram_config/refs/heads/main/data.json"
    private const val ARVAN_BASE_URL = "https://filmapi1.milaadfarzian-tnljt.arvanedge.ir"

    // ─── Shared Preferences Caching ───────────────────────────────────────
    private const val PREFS_NAME = "filigram_remote_config_cache"
    private const val KEY_CACHED_CONFIG = "cached_config_json"
    private const val KEY_CACHED_BANNERS = "cached_banners_json"
    private const val KEY_CACHED_ANNOUNCEMENTS = "cached_announcements_json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Executes an HTTP GET request with cancellable coroutine binding.
     * When cancelled by withTimeoutOrNull, call.cancel() is immediately fired.
     */
    private suspend fun executeGet(url: String, timeoutMs: Long = 3000L): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Filigram-Android-Client/1.2")
                    .header("Accept", "application/json")
                    .build()

                val call = httpClient.newCall(request)
                continuation.invokeOnCancellation {
                    try {
                        call.cancel()
                    } catch (_: Exception) {}
                }

                try {
                    val response = call.execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        continuation.resume(body)
                    } else {
                        AppLogger.w(TAG, "درخواست به $url با کد ${response.code} ناموفق بود")
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "خطا در اتصال به $url: ${e.message}")
                    if (!continuation.isCancelled) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    /**
     * Multi-tiered fallback fetcher:
     * 1. Try Cloudflare (3s limit)
     * 2. Try GitHub Raw (3s limit)
     * 3. Try ArvanCloud (5s limit)
     * 4. Fallback to Local Cached data
     */
    private suspend fun <T> fetchWithFallback(
        cfPath: String,
        ghExtractor: (JSONObject) -> T?,
        arvanPath: String,
        cfArvanParser: (String) -> T?,
        localCacheGetter: () -> T?,
        localCacheSaver: (String) -> Unit
    ): T? {
        // Tier 1: Cloudflare Worker (3s timeout)
        try {
            AppLogger.i(TAG, "[Tier 1] بررسی سرور کلودفلر: $cfPath")
            val cfRaw = executeGet("$CF_BASE_URL$cfPath", timeoutMs = 3000L)
            if (!cfRaw.isNullOrBlank()) {
                val parsed = cfArvanParser(cfRaw)
                if (parsed != null) {
                    AppLogger.s(TAG, "[Tier 1] دریافت موفق از کلودفلر")
                    localCacheSaver(cfRaw)
                    return parsed
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "[Tier 1] شکست کلودفلر: ${e.message}")
        }

        // Tier 2: GitHub Raw Static JSON (3s timeout)
        try {
            AppLogger.i(TAG, "[Tier 2] بررسی پشتیبان گیت‌هاب: $GITHUB_RAW_URL")
            val ghRaw = executeGet(GITHUB_RAW_URL, timeoutMs = 3000L)
            if (!ghRaw.isNullOrBlank()) {
                val rootJson = JSONObject(ghRaw)
                val parsed = ghExtractor(rootJson)
                if (parsed != null) {
                    AppLogger.s(TAG, "[Tier 2] دریافت موفق از پشتیبان گیت‌هاب")
                    return parsed
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "[Tier 2] شکست پشتیبان گیت‌هاب: ${e.message}")
        }

        // Tier 3: ArvanCloud Edge (standard timeout)
        try {
            AppLogger.i(TAG, "[Tier 3] بررسی لایه سوم آروان‌کلاد: $arvanPath")
            val arvanRaw = executeGet("$ARVAN_BASE_URL$arvanPath", timeoutMs = 5000L)
            if (!arvanRaw.isNullOrBlank()) {
                val parsed = cfArvanParser(arvanRaw)
                if (parsed != null) {
                    AppLogger.s(TAG, "[Tier 3] دریافت موفق از آروان‌کلاد")
                    localCacheSaver(arvanRaw)
                    return parsed
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "[Tier 3] شکست لایه سوم آروان‌کلاد: ${e.message}")
        }

        // Tier 4: Local Storage Cache Fallback
        AppLogger.w(TAG, "[Tier 4] تمامی سرورها با خطا مواجه شدند. بازگشت به حافظه کش محلی.")
        return localCacheGetter()
    }

    // ─── 1. App Configurations ──────────────────────────────────────────
    suspend fun getConfigs(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fetchWithFallback(
            cfPath = "/api/config",
            ghExtractor = { root ->
                val cfgObj = root.optJSONObject("configs") ?: return@fetchWithFallback null
                parseConfigMap(cfgObj)
            },
            arvanPath = "/api/config",
            cfArvanParser = { raw ->
                try {
                    val obj = JSONObject(raw)
                    val targetObj = obj.optJSONObject("configs") ?: obj
                    parseConfigMap(targetObj)
                } catch (e: Exception) {
                    null
                }
            },
            localCacheGetter = {
                val cached = prefs.getString(KEY_CACHED_CONFIG, null) ?: return@fetchWithFallback emptyMap()
                try {
                    parseConfigMap(JSONObject(cached))
                } catch (_: Exception) {
                    emptyMap()
                }
            },
            localCacheSaver = { raw ->
                prefs.edit().putString(KEY_CACHED_CONFIG, raw).apply()
            }
        ) ?: emptyMap()
    }

    private fun parseConfigMap(obj: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.optString(k, "")
        }
        return map
    }

    // ─── 2. Banners ─────────────────────────────────────────────────────
    suspend fun getBanners(context: Context): List<AppBanner> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fetchWithFallback(
            cfPath = "/api/banners",
            ghExtractor = { root ->
                val arr = root.optJSONArray("banners") ?: return@fetchWithFallback null
                parseBannersList(arr)
            },
            arvanPath = "/api/banners",
            cfArvanParser = { raw ->
                try {
                    val arr = extractJsonArray(raw, "banners")
                    parseBannersList(arr)
                } catch (e: Exception) {
                    null
                }
            },
            localCacheGetter = {
                val cached = prefs.getString(KEY_CACHED_BANNERS, null) ?: return@fetchWithFallback emptyList()
                try {
                    val arr = extractJsonArray(cached, "banners")
                    parseBannersList(arr)
                } catch (_: Exception) {
                    emptyList()
                }
            },
            localCacheSaver = { raw ->
                prefs.edit().putString(KEY_CACHED_BANNERS, raw).apply()
            }
        ) ?: emptyList()
    }

    private fun parseBannersList(arr: JSONArray): List<AppBanner> {
        val list = mutableListOf<AppBanner>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "banner-$i")
            val img = item.optString("imageUrl", item.optString("image", ""))
            if (img.isBlank()) continue
            val target = if (item.has("targetUrl")) item.optString("targetUrl") else if (item.has("url")) item.optString("url") else null
            val order = item.optInt("order", i)
            val title = if (item.has("title")) item.optString("title") else null
            list.add(AppBanner(id = id, imageUrl = img, targetUrl = target, order = order, title = title))
        }
        list.sortBy { it.order }
        return list
    }

    // ─── 3. Announcements ───────────────────────────────────────────────
    suspend fun getAnnouncements(context: Context): List<Announcement> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val readIds = AnnouncementsManager.getReadIds(context)

        val rawList = fetchWithFallback(
            cfPath = "/chekhabar",
            ghExtractor = { root ->
                val arr = root.optJSONArray("announcements") ?: return@fetchWithFallback null
                parseAnnouncementsList(arr, readIds)
            },
            arvanPath = "/chekhabar",
            cfArvanParser = { raw ->
                try {
                    val arr = extractJsonArray(raw, "announcements")
                    parseAnnouncementsList(arr, readIds)
                } catch (e: Exception) {
                    null
                }
            },
            localCacheGetter = {
                val cached = prefs.getString(KEY_CACHED_ANNOUNCEMENTS, null) ?: return@fetchWithFallback emptyList()
                try {
                    val arr = extractJsonArray(cached, "announcements")
                    parseAnnouncementsList(arr, readIds)
                } catch (_: Exception) {
                    emptyList()
                }
            },
            localCacheSaver = { raw ->
                prefs.edit().putString(KEY_CACHED_ANNOUNCEMENTS, raw).apply()
            }
        ) ?: emptyList()

        // Include default welcome notice if empty
        if (rawList.isEmpty()) {
            val welcomeId = "filigram_welcome_beta_v1"
            val welcomeTitle = "خوش‌آمدید به فیلیگرام (نسخه آزمایشی Beta) 🎬"
            val welcomeText = "به نسخه بتا اپلیکیشن اختصاصی فیلیگرام خوش آمدید!\n\n" +
                    "این نسخه آزمایشی است و پیوسته در حال توسعه و بهبود سرعت است.\n" +
                    "• هرگونه نظر، پیشنهاد، انتقاد یا گزارش باگ و خطا را مستقیماً در تلگرام به آیدی @kiorcode ارسال نمایید.\n" +
                    "• جهت دریافت آخرین به‌روزرسانی‌ها، اخبار سرورها و قسمت‌های جدید در کانال تلگرام فیلیگرام (@filigramapp) عضو شوید."
            val welcomeDate = "2026-09-13T00:00:00.000Z"
            val isWelcomeRead = readIds.contains(welcomeId)
            listOf(Announcement(welcomeId, welcomeTitle, welcomeText, welcomeDate, isWelcomeRead))
        } else {
            rawList
        }
    }

    private fun parseAnnouncementsList(arr: JSONArray, readIds: Set<String>): List<Announcement> {
        val list = mutableListOf<Announcement>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "announcement-$i")
            val title = obj.optString("title", "اعلان رسمی فیلیگرام")
            val text = obj.optString("text", "")
            val createdAt = obj.optString("createdAt", "")
            val views = obj.optInt("views", 0)
            val isRead = readIds.contains(id)
            list.add(Announcement(id, title, text, createdAt, isRead, views))
        }
        return list
    }

    private fun extractJsonArray(raw: String, fieldName: String): JSONArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed)
        }
        val root = JSONObject(trimmed)
        return root.optJSONArray(fieldName)
            ?: root.optJSONArray("value")
            ?: root.optJSONArray("data")
            ?: JSONArray()
    }
}
