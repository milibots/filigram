package com.filigram.cinema

import android.content.Context
import org.json.JSONArray

object SearchHistoryManager {

    private const val PREFS_NAME = "filigram_search_prefs"
    private const val KEY_SEARCH_HISTORY = "search_history"
    private const val MAX_RECENT_SEARCHES = 20

    fun getQueries(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val q = arr.optString(i)
                if (q.isNotBlank()) list.add(q)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuery(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val current = getQueries(context).toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        if (current.size > MAX_RECENT_SEARCHES) {
            current.removeAt(current.size - 1)
        }
        saveQueries(context, current)
    }

    fun removeQuery(context: Context, query: String) {
        val current = getQueries(context).toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        saveQueries(context, current)
    }

    fun clearQueries(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    private fun saveQueries(context: Context, queries: List<String>) {
        val arr = JSONArray()
        for (q in queries) {
            arr.put(q)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }
}
