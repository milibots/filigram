package com.filigram.cinema

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FavoritesManager {

    private const val FILE_NAME = "filigram_favorites.json"
    private val favoritesMap = mutableMapOf<Int, MovieItem>()
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
                    val item = MovieItem(
                        id = id,
                        title = obj.optString("title"),
                        image = obj.optString("image"),
                        type = obj.optInt("type", 0),
                        year = if (obj.has("year")) obj.optString("year") else null,
                        hasSub = obj.optBoolean("hasSub", false),
                        hasDub = obj.optBoolean("hasDub", false),
                        slug = if (obj.has("slug")) obj.optString("slug") else null
                    )
                    favoritesMap[id] = item
                }
                AppLogger.i("FavoritesManager", "تعداد ${favoritesMap.size} فیلم موردعلاقه از حافظه دستگاه بارگذاری شد.")
            } catch (e: Exception) {
                AppLogger.e("FavoritesManager", "خطا در بارگذاری علاقه‌مندی‌ها: ${e.message}")
            }
        }
        isInitialized = true
    }

    @Synchronized
    fun isFavorite(id: Int): Boolean = favoritesMap.containsKey(id)

    @Synchronized
    fun toggleFavorite(context: Context, item: MovieItem): Boolean {
        init(context)
        val isNowFav = if (favoritesMap.containsKey(item.id)) {
            favoritesMap.remove(item.id)
            false
        } else {
            favoritesMap[item.id] = item
            true
        }
        saveToFile(context)
        return isNowFav
    }

    @Synchronized
    fun getFavorites(context: Context): List<MovieItem> {
        init(context)
        return favoritesMap.values.toList().reversed()
    }

    @Synchronized
    fun clearAllFavorites(context: Context): Boolean {
        init(context)
        favoritesMap.clear()
        saveToFile(context)
        return true
    }

    private fun saveToFile(context: Context) {
        try {
            val arr = JSONArray()
            for (it in favoritesMap.values) {
                val obj = JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("image", it.image)
                    put("type", it.type)
                    put("year", it.year)
                    put("hasSub", it.hasSub)
                    put("hasDub", it.hasDub)
                    put("slug", it.slug)
                }
                arr.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            AppLogger.e("FavoritesManager", "خطا در ذخیره علاقه‌مندی‌ها: ${e.message}")
        }
    }
}
