package com.filigram.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: String,
    var isRead: Boolean = false
) {
    fun getFormattedDate(): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoFormat.parse(createdAt) ?: return createdAt
            val outFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            outFormat.format(date)
        } catch (e: Exception) {
            createdAt.take(16).replace("T", " ")
        }
    }
}

object AnnouncementsManager {

    private const val PREFS_NAME = "filigram_announcements_prefs"
    private const val KEY_READ_IDS = "read_announcement_ids"
    private const val ENDPOINT_URL = "https://filigramv1.miladjobs22.workers.dev/chekhabar"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private var cachedAnnouncements: List<Announcement> = emptyList()

    suspend fun fetchAnnouncements(context: Context): List<Announcement> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ENDPOINT_URL)
                .header("User-Agent", "Filigram-Android-Client/1.2")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext cachedAnnouncements
            }

            val body = response.body?.string() ?: return@withContext cachedAnnouncements
            val jsonArray = JSONArray(body)
            val readIds = getReadIds(context)

            val list = mutableListOf<Announcement>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.optString("title", "اعلان رسمی فیلیگرام")
                val text = obj.optString("text", "")
                val createdAt = obj.optString("createdAt", "")
                val isRead = readIds.contains(id)

                list.add(Announcement(id, title, text, createdAt, isRead))
            }
            cachedAnnouncements = list
            list
        } catch (e: Exception) {
            e.printStackTrace()
            cachedAnnouncements
        }
    }

    fun getCachedAnnouncements(): List<Announcement> = cachedAnnouncements

    fun getUnreadCount(context: Context): Int {
        val readIds = getReadIds(context)
        return cachedAnnouncements.count { !readIds.contains(it.id) }
    }

    fun markAsRead(context: Context, id: String) {
        val readIds = getReadIds(context).toMutableSet()
        readIds.add(id)
        saveReadIds(context, readIds)
        cachedAnnouncements.find { it.id == id }?.isRead = true
    }

    fun markAllAsRead(context: Context) {
        val readIds = getReadIds(context).toMutableSet()
        for (item in cachedAnnouncements) {
            readIds.add(item.id)
            item.isRead = true
        }
        saveReadIds(context, readIds)
    }

    private fun getReadIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_READ_IDS, ids).apply()
    }
}
