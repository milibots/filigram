package com.filigram.cinema.download

import com.filigram.cinema.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class DownloadChunkWorker(
    private val client: OkHttpClient,
    private val url: String,
    private val startByte: Long,
    private val endByte: Long,
    private val segmentFile: File,
    private val totalProgressCounter: AtomicLong,
    private val isCancelled: AtomicBoolean,
    private val isPaused: AtomicBoolean
) {
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        val expectedLength = endByte - startByte + 1
        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts && !isCancelled.get() && !isPaused.get()) {
            attempts++
            val existingLength = if (segmentFile.exists()) segmentFile.length() else 0L
            val actualStart = startByte + existingLength

            if (actualStart > endByte || existingLength >= expectedLength) {
                return@withContext true
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=$actualStart-$endByte")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            var raf: RandomAccessFile? = null
            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206 && response.code != 200) {
                    AppLogger.w("DownloadChunkWorker", "پاسخ ناموفق در قطعه $actualStart-$endByte (کد: ${response.code}) - تلاش $attempts از $maxAttempts")
                    response.close()
                    if (attempts < maxAttempts && !isCancelled.get() && !isPaused.get()) {
                        delay(1000)
                        continue
                    }
                    return@withContext false
                }

                val body = response.body ?: run {
                    response.close()
                    return@withContext false
                }

                raf = RandomAccessFile(segmentFile, "rw")
                raf.seek(existingLength)

                val buffer = ByteArray(64 * 1024)
                val inputStream = body.byteStream()
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get() || isPaused.get()) {
                        break
                    }
                    raf.write(buffer, 0, bytesRead)
                    totalProgressCounter.addAndGet(bytesRead.toLong())
                }

                raf.close()
                raf = null
                inputStream.close()
                body.close()
                response.close()

                if (isCancelled.get() || isPaused.get()) {
                    return@withContext false
                }

                if (segmentFile.exists() && segmentFile.length() >= expectedLength) {
                    return@withContext true
                }
            } catch (e: Exception) {
                AppLogger.w("DownloadChunkWorker", "خطا در دریافت قطعه $startByte-$endByte (تلاش $attempts): ${e.message}")
                if (attempts < maxAttempts && !isCancelled.get() && !isPaused.get()) {
                    delay(1000)
                }
            } finally {
                try {
                    raf?.close()
                } catch (_: Exception) {}
            }
        }

        return@withContext segmentFile.exists() && segmentFile.length() >= expectedLength
    }
}
