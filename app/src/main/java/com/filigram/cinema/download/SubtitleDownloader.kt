package com.filigram.cinema.download

import com.filigram.cinema.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object SubtitleDownloader {

    private const val TAG = "SubtitleDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadSubtitleForMedia(
        mediaId: Int,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        targetVideoFile: File,
        directSubUrl: String? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val baseName = targetVideoFile.nameWithoutExtension
            val parentDir = targetVideoFile.parentFile ?: return@withContext null
            val destSubFile = File(parentDir, "$baseName.srt")

            if (destSubFile.exists() && destSubFile.length() > 100) {
                return@withContext destSubFile
            }

            var subUrl = directSubUrl

            if (subUrl.isNullOrEmpty()) {
                subUrl = findSubtitleUrl(mediaId, isSeries, season, episode)
            }

            if (subUrl.isNullOrEmpty()) {
                return@withContext null
            }

            AppLogger.i(TAG, "در حال دانلود زیرنویس هماهنگ: $subUrl")
            val req = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val res = client.newCall(req).execute()
            if (!res.isSuccessful) {
                res.close()
                return@withContext null
            }

            val rawBytes = res.body?.bytes() ?: return@withContext null
            res.close()

            val fixedContent = sanitizePersianSubtitle(rawBytes)
            destSubFile.writeText(fixedContent, Charsets.UTF_8)
            AppLogger.s(TAG, "زیرنویس هماهنگ ذخیره شد: ${destSubFile.name}")
            return@withContext destSubFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دانلود زیرنویس: ${e.message}")
            return@withContext null
        }
    }

    private suspend fun findSubtitleUrl(
        mediaId: Int,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://expertappmedia.org/api-v1/movie/detail-info"
            val body = okhttp3.FormBody.Builder()
                .add("id", mediaId.toString())
                .build()
            val req = Request.Builder().url(url).post(body).build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "{}")
                res.close()
                val info = json.optJSONObject("info")
                val subUrl = info?.optString("subtitle")?.takeIf { it.isNotBlank() }
                    ?: info?.optString("sub_url")?.takeIf { it.isNotBlank() }
                if (!subUrl.isNullOrEmpty()) {
                    return@withContext subUrl
                }
            }
        } catch (_: Exception) {}
        null
    }

    fun sanitizePersianSubtitle(rawBytes: ByteArray): String {
        return try {
            val utf8Candidate = String(rawBytes, Charsets.UTF_8)
            val hasPersianChars = utf8Candidate.any { it in '\u0600'..'\u06FF' }
            val hasReplacementChar = utf8Candidate.contains('\uFFFD')

            if (hasPersianChars && !hasReplacementChar) {
                utf8Candidate
            } else {
                val win1256Charset = try {
                    Charset.forName("windows-1256")
                } catch (_: Exception) {
                    Charsets.UTF_8
                }
                val win1256Candidate = String(rawBytes, win1256Charset)
                val winPersian = win1256Candidate.any { it in '\u0600'..'\u06FF' }
                if (winPersian) win1256Candidate else utf8Candidate
            }
        } catch (_: Exception) {
            String(rawBytes, Charsets.UTF_8)
        }
    }
}
