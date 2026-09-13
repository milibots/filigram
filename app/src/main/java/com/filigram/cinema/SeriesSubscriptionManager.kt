package com.filigram.cinema

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SeriesSubscription(
    val id: Int,
    val title: String,
    val image: String,
    val engine: String = "movielix",
    val slug: String? = null,
    var lastKnownSeason: Int = 1,
    var lastKnownEpisode: Int = 0,
    var lastKnownEpisodeTitle: String? = null,
    var isEnabled: Boolean = true,
    val subscribedAt: Long = System.currentTimeMillis()
)

object SeriesSubscriptionManager {

    private const val FILE_NAME = "filigram_series_subscriptions.json"
    private val subscriptionsMap = mutableMapOf<Int, SeriesSubscription>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optInt("id")
                    val sub = SeriesSubscription(
                        id = id,
                        title = obj.optString("title"),
                        image = obj.optString("image"),
                        engine = obj.optString("engine", "movielix"),
                        slug = if (obj.has("slug")) obj.optString("slug") else null,
                        lastKnownSeason = obj.optInt("lastKnownSeason", 1),
                        lastKnownEpisode = obj.optInt("lastKnownEpisode", 0),
                        lastKnownEpisodeTitle = if (obj.has("lastKnownEpisodeTitle")) obj.optString("lastKnownEpisodeTitle") else null,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        subscribedAt = obj.optLong("subscribedAt", System.currentTimeMillis())
                    )
                    subscriptionsMap[id] = sub
                }
                AppLogger.i("SeriesSubscriptionManager", "تعداد ${subscriptionsMap.size} سریال دنبال‌شده بارگذاری شد.")
            } catch (e: Exception) {
                AppLogger.e("SeriesSubscriptionManager", "خطا در بارگذاری اشتراک‌های سریال: ${e.message}")
            }
        }
        isInitialized = true
    }

    @Synchronized
    fun isSubscribed(id: Int): Boolean {
        val sub = subscriptionsMap[id]
        return sub != null && sub.isEnabled
    }

    @Synchronized
    fun getSubscription(id: Int): SeriesSubscription? = subscriptionsMap[id]

    @Synchronized
    fun toggleSubscription(
        context: Context,
        item: MovieItem,
        engine: String,
        season: Int = 1,
        episode: Int = 0,
        epTitle: String? = null
    ): Boolean {
        init(context)
        val existing = subscriptionsMap[item.id]
        val isNowSubscribed: Boolean
        if (existing != null) {
            existing.isEnabled = !existing.isEnabled
            if (existing.isEnabled) {
                if (season > 0) existing.lastKnownSeason = season
                if (episode > 0) existing.lastKnownEpisode = episode
                if (!epTitle.isNullOrEmpty()) existing.lastKnownEpisodeTitle = epTitle
            }
            isNowSubscribed = existing.isEnabled
        } else {
            val newSub = SeriesSubscription(
                id = item.id,
                title = item.title,
                image = item.image,
                engine = engine,
                slug = item.slug,
                lastKnownSeason = if (season > 0) season else 1,
                lastKnownEpisode = episode,
                lastKnownEpisodeTitle = epTitle,
                isEnabled = true
            )
            subscriptionsMap[item.id] = newSub
            isNowSubscribed = true
        }
        saveToFile(context)
        return isNowSubscribed
    }

    @Synchronized
    fun updateLastKnown(
        context: Context,
        id: Int,
        season: Int,
        episode: Int,
        epTitle: String?
    ) {
        init(context)
        subscriptionsMap[id]?.let { sub ->
            sub.lastKnownSeason = season
            sub.lastKnownEpisode = episode
            sub.lastKnownEpisodeTitle = epTitle
            saveToFile(context)
        }
    }

    @Synchronized
    fun getAllSubscriptions(context: Context): List<SeriesSubscription> {
        init(context)
        return subscriptionsMap.values.toList()
    }

    @Synchronized
    fun getAllActiveSubscriptions(context: Context): List<SeriesSubscription> {
        init(context)
        return subscriptionsMap.values.filter { it.isEnabled }
    }

    @Synchronized
    private fun saveToFile(context: Context) {
        try {
            val arr = JSONArray()
            for (sub in subscriptionsMap.values) {
                val obj = JSONObject().apply {
                    put("id", sub.id)
                    put("title", sub.title)
                    put("image", sub.image)
                    put("engine", sub.engine)
                    put("slug", sub.slug)
                    put("lastKnownSeason", sub.lastKnownSeason)
                    put("lastKnownEpisode", sub.lastKnownEpisode)
                    put("lastKnownEpisodeTitle", sub.lastKnownEpisodeTitle)
                    put("isEnabled", sub.isEnabled)
                    put("subscribedAt", sub.subscribedAt)
                }
                arr.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            AppLogger.e("SeriesSubscriptionManager", "خطا در ذخیره اشتراک‌های سریال: ${e.message}")
        }
    }
}
