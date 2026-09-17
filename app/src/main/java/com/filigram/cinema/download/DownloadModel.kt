package com.filigram.cinema.download

import org.json.JSONObject

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val mediaId: Int,
    val title: String,
    val seriesTitle: String? = null,
    val season: Int = -1,
    val episode: Int = -1,
    val qualityLabel: String,
    var url: String,
    val filePath: String,
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var partsCount: Int = 4,
    var speedBytesPerSec: Long = 0L,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var errorMessage: String? = null,
    var subtitlePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSeries: Boolean
        get() = season > 0 && episode > 0

    val progressPercent: Int
        get() {
            if (totalBytes <= 0L) return 0
            return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("mediaId", mediaId)
            put("title", title)
            put("seriesTitle", seriesTitle ?: "")
            put("season", season)
            put("episode", episode)
            put("qualityLabel", qualityLabel)
            put("url", url)
            put("filePath", filePath)
            put("totalBytes", totalBytes)
            put("downloadedBytes", downloadedBytes)
            put("partsCount", partsCount)
            put("status", status.name)
            put("errorMessage", errorMessage ?: "")
            put("subtitlePath", subtitlePath ?: "")
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DownloadTask {
            val statusStr = json.optString("status", DownloadStatus.QUEUED.name)
            val parsedStatus = try {
                DownloadStatus.valueOf(statusStr)
            } catch (_: Exception) {
                DownloadStatus.QUEUED
            }

            return DownloadTask(
                id = json.getString("id"),
                mediaId = json.optInt("mediaId", 0),
                title = json.getString("title"),
                seriesTitle = json.optString("seriesTitle").takeIf { it.isNotEmpty() },
                season = json.optInt("season", -1),
                episode = json.optInt("episode", -1),
                qualityLabel = json.optString("qualityLabel", ""),
                url = json.getString("url"),
                filePath = json.getString("filePath"),
                totalBytes = json.optLong("totalBytes", 0L),
                downloadedBytes = json.optLong("downloadedBytes", 0L),
                partsCount = json.optInt("partsCount", 4),
                status = if (parsedStatus == DownloadStatus.DOWNLOADING || parsedStatus == DownloadStatus.CONNECTING) DownloadStatus.PAUSED else parsedStatus,
                errorMessage = json.optString("errorMessage").takeIf { it.isNotEmpty() },
                subtitlePath = json.optString("subtitlePath").takeIf { it.isNotEmpty() },
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class DownloadSettings(
    var maxConcurrentTasks: Int = 2,
    var threadsPerTask: Int = 4,
    var wifiOnly: Boolean = false,
    var nightSchedulerEnabled: Boolean = false,
    var nightStartHour: Int = 2,
    var nightEndHour: Int = 7,
    var autoDownloadSubtitles: Boolean = true,
    var customStoragePath: String? = null
)
