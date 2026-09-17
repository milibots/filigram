package com.filigram.cinema

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object HistoryManager {

    private const val FILE_NAME = "filigram_history.json"
    private const val MAX_HISTORY_ITEMS = 200
    private val historyList = mutableListOf<MovieItem>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                val arr = JSONArray(jsonStr)
                historyList.clear()
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
                    historyList.add(item)
                }
                AppLogger.i("HistoryManager", "تعداد ${historyList.size} عنوان بازدیدشده از حافظه دستگاه بارگذاری شد.")
            } catch (e: Exception) {
                AppLogger.e("HistoryManager", "خطا در بارگذاری تاریخچه: ${e.message}")
            }
        }
        isInitialized = true
    }

    @Synchronized
    fun addVisit(context: Context, item: MovieItem) {
        init(context)

        historyList.removeAll { it.id == item.id }
        historyList.add(0, item)
        if (historyList.size > MAX_HISTORY_ITEMS) {
            historyList.removeAt(historyList.size - 1)
        }
        saveToFile(context)
    }

    @Synchronized
    fun getHistory(context: Context): List<MovieItem> {
        init(context)
        return historyList.toList()
    }

    @Synchronized
    fun removeVisit(context: Context, id: Int) {
        init(context)
        historyList.removeAll { it.id == id }
        saveToFile(context)
    }

    @Synchronized
    fun clearAllHistory(context: Context) {
        init(context)
        historyList.clear()
        saveToFile(context)
    }

    @Synchronized
    fun getCount(context: Context): Int {
        init(context)
        return historyList.size
    }

    private fun saveToFile(context: Context) {
        try {
            val arr = JSONArray()
            for (item in historyList) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("title", item.title)
                obj.put("image", item.image)
                obj.put("type", item.type)
                item.year?.let { obj.put("year", it) }
                obj.put("hasSub", item.hasSub)
                obj.put("hasDub", item.hasDub)
                item.slug?.let { obj.put("slug", it) }
                arr.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(arr.toString())
        } catch (e: Exception) {
            AppLogger.e("HistoryManager", "خطا در ذخیره‌سازی تاریخچه: ${e.message}")
        }
    }
}
