package com.filigram.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: String,
    var isRead: Boolean = false,
    val views: Int = 0
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

    private var cachedAnnouncements: List<Announcement> = emptyList()

    suspend fun fetchAnnouncements(context: Context): List<Announcement> = withContext(Dispatchers.IO) {
        val list = RemoteConfigRepository.getAnnouncements(context)
        cachedAnnouncements = list
        list
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

    fun getReadIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_READ_IDS, ids).apply()
    }
}
