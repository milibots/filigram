package com.filigram.cinema

import android.content.Context
import org.json.JSONObject
import java.io.File

object PlaybackProgressManager {

    private const val FILE_NAME = "filigram_playback_progress.json"
    private val progressMap = mutableMapOf<String, Long>()
    private val durationMap = mutableMapOf<String, Long>()
    private var isLoaded = false

    @Synchronized
    fun init(context: Context) {
        if (isLoaded) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val root = JSONObject(file.readText())
                val posObj = root.optJSONObject("positions") ?: JSONObject()
                val durObj = root.optJSONObject("durations") ?: JSONObject()

                posObj.keys().forEach { k ->
                    progressMap[k] = posObj.optLong(k, 0L)
                }
                durObj.keys().forEach { k ->
                    durationMap[k] = durObj.optLong(k, 0L)
                }
            } catch (_: Exception) {}
        }
        isLoaded = true
    }

    @Synchronized
    fun saveProgress(context: Context, key: String, positionMs: Long, durationMs: Long) {
        init(context)
        if (positionMs <= 0L) return
        if (durationMs > 0 && positionMs >= durationMs - 15000L) {
            progressMap.remove(key)
        } else {
            progressMap[key] = positionMs
            if (durationMs > 0) durationMap[key] = durationMs
        }
        saveToFile(context)
    }

    @Synchronized
    fun getProgress(context: Context, key: String): Long {
        init(context)
        return progressMap[key] ?: 0L
    }

    @Synchronized
    fun getDuration(context: Context, key: String): Long {
        init(context)
        return durationMap[key] ?: 0L
    }

    @Synchronized
    fun clearProgress(context: Context, key: String) {
        init(context)
        progressMap.remove(key)
        durationMap.remove(key)
        saveToFile(context)
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000
        val hr = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (hr > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hr, min, sec)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", min, sec)
        }
    }

    private fun saveToFile(context: Context) {
        try {
            val root = JSONObject()
            val posObj = JSONObject()
            val durObj = JSONObject()

            progressMap.forEach { (k, v) -> posObj.put(k, v) }
            durationMap.forEach { (k, v) -> durObj.put(k, v) }

            root.put("positions", posObj)
            root.put("durations", durObj)

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(root.toString())
        } catch (_: Exception) {}
    }
}
