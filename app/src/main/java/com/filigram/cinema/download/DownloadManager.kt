package com.filigram.cinema.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import com.filigram.cinema.AppLogger
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DownloadManager {

    private const val TAG = "DownloadManager"
    private const val PREF_NAME = "filigram_download_prefs"
    private const val KEY_SETTINGS = "download_settings"
    private const val KEY_TASKS = "download_tasks"

    private val tasks = mutableListOf<DownloadTask>()
    val settings = DownloadSettings(maxConcurrentTasks = 2, threadsPerTask = 4)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pauseSignals = ConcurrentHashMap<String, AtomicBoolean>()
    private val cancelSignals = ConcurrentHashMap<String, AtomicBoolean>()
    private val progressCounters = ConcurrentHashMap<String, AtomicLong>()

    private val listeners = mutableListOf<DownloadListener>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun sanitizeDownloadUrl(url: String): String {
        if (url.contains("expertappmedia.org")) {
            return url.replace(Regex("pro([0-9]+)\\.expertappmedia\\.org"), "pro$1sub.expertapp.org")
        }
        return url
    }

    fun getCandidateUrls(originalUrl: String): List<String> {
        val list = mutableListOf<String>()
        val sanitized = sanitizeDownloadUrl(originalUrl)
        if (sanitized != originalUrl) {
            list.add(sanitized)
        }
        list.add(originalUrl)
        if (originalUrl.contains("sub.expertapp.org")) {
            val fallback = originalUrl.replace(Regex("pro([0-9]+)sub\\.expertapp\\.org"), "pro$1.expertappmedia.org")
            if (fallback != originalUrl && !list.contains(fallback)) {
                list.add(fallback)
            }
        }
        return list
    }

    private var isInitialized = false

    interface DownloadListener {
        fun onTaskUpdated(task: DownloadTask)
        fun onQueueChanged()
        fun onTotalSpeedUpdated(totalBytesPerSec: Long, activeCount: Int)
    }

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadSettings(context)
        loadTasks(context)
        startSpeedMonitor()
    }

    fun addListener(listener: DownloadListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: DownloadListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyTaskUpdated(task: DownloadTask) {
        synchronized(listeners) {
            for (listener in listeners) {
                listener.onTaskUpdated(task)
            }
        }
    }

    private fun notifyQueueChanged() {
        synchronized(listeners) {
            for (listener in listeners) {
                listener.onQueueChanged()
            }
        }
    }

    private fun notifySpeedUpdated(speed: Long, count: Int) {
        synchronized(listeners) {
            for (listener in listeners) {
                listener.onTotalSpeedUpdated(speed, count)
            }
        }
    }

    @Synchronized
    fun getTasks(): List<DownloadTask> {
        return tasks.toList()
    }

    fun getActiveDownloadsCount(): Int {
        return tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING }
    }

    fun getTotalSpeed(): Long {
        return tasks.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }
    }

    @Synchronized
    fun updateSettings(
        context: Context,
        maxConcurrent: Int,
        threads: Int,
        wifiOnly: Boolean,
        nightScheduler: Boolean,
        nightStart: Int,
        nightEnd: Int,
        autoSubtitles: Boolean,
        customPath: String? = null
    ) {
        settings.maxConcurrentTasks = maxConcurrent.coerceIn(1, 6)
        settings.threadsPerTask = threads.coerceIn(1, 8)
        settings.wifiOnly = wifiOnly
        settings.nightSchedulerEnabled = nightScheduler
        settings.nightStartHour = nightStart
        settings.nightEndHour = nightEnd
        settings.autoDownloadSubtitles = autoSubtitles
        if (customPath != null) {
            settings.customStoragePath = customPath
        }
        saveSettings(context)
        processQueue(context)
    }

    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun isWithinNightSchedule(): Boolean {
        if (!settings.nightSchedulerEnabled) return true
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val start = settings.nightStartHour
        val end = settings.nightEndHour

        return if (start <= end) {
            currentHour in start until end
        } else {
            currentHour >= start || currentHour < end
        }
    }

    private fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SETTINGS, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val json = JSONObject(jsonStr)
                settings.maxConcurrentTasks = json.optInt("maxConcurrent", 2)
                settings.threadsPerTask = json.optInt("threads", 4)
                settings.wifiOnly = json.optBoolean("wifiOnly", false)
                settings.nightSchedulerEnabled = json.optBoolean("nightScheduler", false)
                settings.nightStartHour = json.optInt("nightStart", 2)
                settings.nightEndHour = json.optInt("nightEnd", 7)
                settings.autoDownloadSubtitles = json.optBoolean("autoSubtitles", true)
                settings.customStoragePath = json.optString("customStoragePath").takeIf { it.isNotBlank() }
            } catch (_: Exception) {}
        }
    }

    private fun saveSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("maxConcurrent", settings.maxConcurrentTasks)
            put("threads", settings.threadsPerTask)
            put("wifiOnly", settings.wifiOnly)
            put("nightScheduler", settings.nightSchedulerEnabled)
            put("nightStart", settings.nightStartHour)
            put("nightEnd", settings.nightEndHour)
            put("autoSubtitles", settings.autoDownloadSubtitles)
            put("customStoragePath", settings.customStoragePath ?: "")
        }
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    private fun loadTasks(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TASKS, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                tasks.clear()
                var needsSave = false
                for (i in 0 until arr.length()) {
                    val task = DownloadTask.fromJson(arr.getJSONObject(i))
                    val fixedUrl = sanitizeDownloadUrl(task.url)
                    if (fixedUrl != task.url) {
                        task.url = fixedUrl
                        needsSave = true
                    }
                    if (task.status == DownloadStatus.FAILED && (task.errorMessage?.contains("pro1.expertappmedia.org") == true || task.errorMessage?.contains("25000ms") == true)) {
                        task.status = DownloadStatus.QUEUED
                        task.errorMessage = null
                        needsSave = true
                    }
                    tasks.add(task)
                }
                if (needsSave) {
                    saveTasks(context)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در بارگیری وظایف دانلود: ${e.message}")
            }
        }
    }

    private fun saveTasks(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray()
            synchronized(tasks) {
                for (task in tasks) {
                    arr.put(task.toJson())
                }
            }
            prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در ذخیره وظایف دانلود: ${e.message}")
        }
    }

    fun enqueue(
        context: Context,
        mediaId: Int,
        title: String,
        seriesTitle: String? = null,
        season: Int = -1,
        episode: Int = -1,
        qualityLabel: String,
        url: String
    ): DownloadTask {
        val downloadDir = getDownloadFolder(context)
        val safeFileName = sanitizeFilename("$title.mp4")
        val targetFile = File(downloadDir, safeFileName)
        val safeUrl = sanitizeDownloadUrl(url)

        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            mediaId = mediaId,
            title = title,
            seriesTitle = seriesTitle,
            season = season,
            episode = episode,
            qualityLabel = qualityLabel,
            url = safeUrl,
            filePath = targetFile.absolutePath,
            partsCount = settings.threadsPerTask,
            status = DownloadStatus.QUEUED
        )

        synchronized(tasks) {
            tasks.add(0, task)
        }
        saveTasks(context)
        notifyQueueChanged()
        processQueue(context)
        return task
    }

    fun enqueueBatch(context: Context, batch: List<DownloadTask>) {
        if (batch.isEmpty()) return
        for (task in batch) {
            task.url = sanitizeDownloadUrl(task.url)
        }
        synchronized(tasks) {
            tasks.addAll(0, batch)
        }
        saveTasks(context)
        notifyQueueChanged()
        processQueue(context)
    }

    @Synchronized
    fun pauseTask(context: Context, taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING) {
            pauseSignals[taskId]?.set(true)
            activeJobs[taskId]?.cancel()
            task.status = DownloadStatus.PAUSED
            task.speedBytesPerSec = 0L
            saveTasks(context)
            notifyTaskUpdated(task)
            processQueue(context)
        }
    }

    @Synchronized
    fun resumeTask(context: Context, taskId: String) {
        val task = tasks.find { it.id == taskId } ?: return
        if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
            task.status = DownloadStatus.QUEUED
            task.errorMessage = null
            saveTasks(context)
            notifyTaskUpdated(task)
            processQueue(context)
        }
    }

    @Synchronized
    fun pauseAll(context: Context) {
        for (task in tasks) {
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONNECTING || task.status == DownloadStatus.QUEUED) {
                pauseSignals[task.id]?.set(true)
                activeJobs[task.id]?.cancel()
                task.status = DownloadStatus.PAUSED
                task.speedBytesPerSec = 0L
            }
        }
        saveTasks(context)
        notifyQueueChanged()
    }

    @Synchronized
    fun resumeAll(context: Context) {
        for (task in tasks) {
            if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                task.status = DownloadStatus.QUEUED
                task.errorMessage = null
            }
        }
        saveTasks(context)
        notifyQueueChanged()
        processQueue(context)
    }

    @Synchronized
    fun cancelTask(context: Context, taskId: String, deleteFile: Boolean = false) {
        val task = tasks.find { it.id == taskId } ?: return
        cancelSignals[taskId]?.set(true)
        activeJobs[taskId]?.cancel()

        if (deleteFile) {
            try {
                val f = File(task.filePath)
                if (f.exists()) f.delete()
                for (p in 0 until 16) {
                    val seg = File("${task.filePath}.part$p")
                    if (seg.exists()) seg.delete()
                }
            } catch (_: Exception) {}
            tasks.remove(task)
        } else {
            task.status = DownloadStatus.CANCELLED
            task.speedBytesPerSec = 0L
        }

        saveTasks(context)
        notifyQueueChanged()
        processQueue(context)
    }

    @Synchronized
    fun clearCompleted(context: Context) {
        tasks.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELLED }
        saveTasks(context)
        notifyQueueChanged()
    }

    private fun processQueue(context: Context) {
        if (settings.wifiOnly && !isWifiConnected(context)) {
            AppLogger.w(TAG, "دانلودها متوقف شدند: اتصال فقط از طریق Wi-Fi فعال است")
            return
        }

        if (settings.nightSchedulerEnabled && !isWithinNightSchedule()) {
            AppLogger.w(TAG, "دانلودها متوقف شدند: خارج از بازه ساعات شبانه (${settings.nightStartHour}:00 الی ${settings.nightEndHour}:00)")
            return
        }

        val activeCount = tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING }
        val slotsAvailable = settings.maxConcurrentTasks - activeCount
        if (slotsAvailable <= 0) return

        val queuedTasks = tasks.filter { it.status == DownloadStatus.QUEUED }.take(slotsAvailable)
        for (task in queuedTasks) {
            startDownloadExecution(context.applicationContext, task)
        }
    }

    private fun startDownloadExecution(context: Context, task: DownloadTask) {
        task.status = DownloadStatus.CONNECTING
        task.errorMessage = null
        notifyTaskUpdated(task)

        val pauseSig = AtomicBoolean(false)
        val cancelSig = AtomicBoolean(false)
        val counter = AtomicLong(task.downloadedBytes)

        pauseSignals[task.id] = pauseSig
        cancelSignals[task.id] = cancelSig
        progressCounters[task.id] = counter

        val job = scope.launch {
            var isSuccess = false
            try {
                isSuccess = executeDownload(context, task, pauseSig, cancelSig, counter)
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطای عمومی در دانلود [${task.title}]: ${e.message}")
                task.errorMessage = e.message
            } finally {
                activeJobs.remove(task.id)
                pauseSignals.remove(task.id)
                cancelSignals.remove(task.id)
                progressCounters.remove(task.id)
                task.speedBytesPerSec = 0L

                if (isSuccess) {
                    task.status = DownloadStatus.COMPLETED
                    task.downloadedBytes = task.totalBytes
                    MediaScannerConnection.scanFile(context, arrayOf(task.filePath), arrayOf("video/mp4"), null)

                    if (settings.autoDownloadSubtitles) {
                        try {
                            val subFile = SubtitleDownloader.downloadSubtitleForMedia(
                                mediaId = task.mediaId,
                                isSeries = task.isSeries,
                                season = task.season,
                                episode = task.episode,
                                targetVideoFile = File(task.filePath)
                            )
                            if (subFile != null) {
                                task.subtitlePath = subFile.absolutePath
                                MediaScannerConnection.scanFile(context, arrayOf(subFile.absolutePath), arrayOf("application/x-subrip", "text/plain"), null)
                            }
                        } catch (se: Exception) {
                            AppLogger.e(TAG, "خطا در دریافت خودکار زیرنویس: ${se.message}")
                        }
                    }

                    AppLogger.s(TAG, "دانلود با موفقیت تکمیل شد: ${task.title}")
                } else if (pauseSig.get()) {
                    task.status = DownloadStatus.PAUSED
                } else if (cancelSig.get()) {
                    task.status = DownloadStatus.CANCELLED
                } else {
                    task.status = DownloadStatus.FAILED
                    if (task.errorMessage.isNullOrEmpty()) {
                        task.errorMessage = "خطا در اتصال به سرور دانلود"
                    }
                }

                saveTasks(context)
                notifyTaskUpdated(task)
                processQueue(context)
            }
        }
        activeJobs[task.id] = job
    }

    private suspend fun executeDownload(
        context: Context,
        task: DownloadTask,
        pauseSig: AtomicBoolean,
        cancelSig: AtomicBoolean,
        counter: AtomicLong
    ): Boolean = withContext(Dispatchers.IO) {
        val destinationFile = File(task.filePath)
        destinationFile.parentFile?.mkdirs()

        // 1. Fast probe candidate URLs to resolve unblocked mirror and file metadata in <1s
        val candidates = getCandidateUrls(task.url)
        var acceptsRanges = false
        var totalLength = task.totalBytes
        var workingUrl = task.url
        var probeSuccess = false

        for (candidate in candidates) {
            if (pauseSig.get() || cancelSig.get()) return@withContext false
            try {
                val probeReq = Request.Builder()
                    .url(candidate)
                    .header("Range", "bytes=0-1023")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val res = probeClient.newCall(probeReq).execute()
                if (res.code == 206) {
                    acceptsRanges = true
                    val cr = res.header("Content-Range")
                    val totalFromCr = cr?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: 0L
                    if (totalFromCr > 0L) totalLength = totalFromCr
                    workingUrl = candidate
                    probeSuccess = true
                    res.close()
                    break
                } else if (res.isSuccessful) {
                    val cl = res.header("Content-Length")?.toLongOrNull() ?: 0L
                    val ar = res.header("Accept-Ranges")
                    if (cl > 0L) totalLength = cl
                    acceptsRanges = ar?.contains("bytes", ignoreCase = true) == true
                    workingUrl = candidate
                    probeSuccess = true
                    res.close()
                    break
                }
                res.close()
            } catch (e: Exception) {
                AppLogger.w(TAG, "تست سرور $candidate با شکست مواجه شد: ${e.message}")
            }
        }

        task.url = workingUrl
        if (totalLength > 0L) {
            task.totalBytes = totalLength
        }

        if (!probeSuccess && totalLength <= 0L) {
            AppLogger.w(TAG, "سرورهای اولیه پاسخ ندادند، تلاش نهایی با آدرس مستقیم: $workingUrl")
        }

        task.status = DownloadStatus.DOWNLOADING
        notifyTaskUpdated(task)

        val parts = if (acceptsRanges && totalLength > 10 * 1024 * 1024) settings.threadsPerTask else 1
        task.partsCount = parts
        notifyTaskUpdated(task)

        if (parts > 1) {
            val chunkSize = totalLength / parts
            var currentDownloaded = 0L
            for (i in 0 until parts) {
                val seg = File("${task.filePath}.part$i")
                if (seg.exists()) currentDownloaded += seg.length()
            }
            task.downloadedBytes = currentDownloaded
            counter.set(currentDownloaded)

            val partJobs = mutableListOf<Deferred<Boolean>>()
            for (i in 0 until parts) {
                val start = i * chunkSize
                val end = if (i == parts - 1) totalLength - 1 else (start + chunkSize - 1)
                val segFile = File("${task.filePath}.part$i")

                val worker = DownloadChunkWorker(
                    client = okHttpClient,
                    url = task.url,
                    startByte = start,
                    endByte = end,
                    segmentFile = segFile,
                    totalProgressCounter = counter,
                    isCancelled = cancelSig,
                    isPaused = pauseSig
                )

                val deferred = async(Dispatchers.IO) {
                    worker.download()
                }
                partJobs.add(deferred)
            }

            val results = partJobs.awaitAll()
            val allSuccess = results.all { it } && !pauseSig.get() && !cancelSig.get()

            if (allSuccess) {
                mergeSegmentFiles(task.filePath, parts, destinationFile)
                task.downloadedBytes = destinationFile.length()
                return@withContext true
            } else if (pauseSig.get() || cancelSig.get()) {
                task.downloadedBytes = counter.get()
                return@withContext false
            } else {
                AppLogger.w(TAG, "دانلود چندتکه کامل نشد، سوئیچ به استریم مستقیم...")
                return@withContext executeSingleStream(task, destinationFile, totalLength, acceptsRanges, pauseSig, cancelSig, counter)
            }
        } else {
            return@withContext executeSingleStream(task, destinationFile, totalLength, acceptsRanges, pauseSig, cancelSig, counter)
        }
    }

    private suspend fun executeSingleStream(
        task: DownloadTask,
        destinationFile: File,
        totalLength: Long,
        acceptsRanges: Boolean,
        pauseSig: AtomicBoolean,
        cancelSig: AtomicBoolean,
        counter: AtomicLong
    ): Boolean = withContext(Dispatchers.IO) {
        task.partsCount = 1
        notifyTaskUpdated(task)

        val existingLength = if (destinationFile.exists()) destinationFile.length() else 0L
        val reqBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        if (acceptsRanges && existingLength > 0 && (totalLength <= 0 || totalLength > existingLength)) {
            reqBuilder.header("Range", "bytes=$existingLength-")
            counter.set(existingLength)
        } else if (!destinationFile.exists() || !acceptsRanges) {
            counter.set(0L)
        }

        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts && !pauseSig.get() && !cancelSig.get()) {
            attempts++
            try {
                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    if (attempts < maxAttempts && !pauseSig.get() && !cancelSig.get()) {
                        delay(1000)
                        continue
                    }
                    return@withContext false
                }

                val body = response.body ?: run {
                    response.close()
                    return@withContext false
                }

                val raf = RandomAccessFile(destinationFile, "rw")
                if (response.code == 206) {
                    val currentPos = destinationFile.length()
                    raf.seek(currentPos)
                    counter.set(currentPos)
                } else {
                    raf.setLength(0)
                    raf.seek(0)
                    counter.set(0L)
                }

                val buffer = ByteArray(64 * 1024)
                val stream = body.byteStream()
                var read: Int

                while (stream.read(buffer).also { read = it } != -1) {
                    if (pauseSig.get() || cancelSig.get()) break
                    raf.write(buffer, 0, read)
                    counter.addAndGet(read.toLong())
                }

                raf.close()
                stream.close()
                body.close()
                response.close()

                task.downloadedBytes = counter.get()
                if (pauseSig.get() || cancelSig.get()) return@withContext false

                if (totalLength > 0 && destinationFile.length() >= totalLength) {
                    return@withContext true
                }
                if (totalLength <= 0 && destinationFile.length() > 0) {
                    return@withContext true
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "خطا در تک استریم (تلاش $attempts): ${e.message}")
                if (attempts < maxAttempts && !pauseSig.get() && !cancelSig.get()) {
                    delay(1000)
                }
            }
        }

        task.downloadedBytes = counter.get()
        return@withContext destinationFile.exists() && (totalLength <= 0 || destinationFile.length() >= totalLength)
    }

    private fun mergeSegmentFiles(basePath: String, parts: Int, target: File) {
        val fos = FileOutputStream(target)
        val buffer = ByteArray(64 * 1024)
        for (i in 0 until parts) {
            val seg = File("$basePath.part$i")
            if (seg.exists()) {
                val fis = FileInputStream(seg)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
                fis.close()
                seg.delete()
            }
        }
        fos.flush()
        fos.close()
    }

    private fun startSpeedMonitor() {
        val previousDownloaded = ConcurrentHashMap<String, Long>()

        scope.launch {
            while (isActive) {
                delay(1000)
                var totalSpeed = 0L
                var activeCount = 0

                for (task in tasks) {
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        activeCount++
                        val currentBytes = progressCounters[task.id]?.get() ?: task.downloadedBytes
                        val prev = previousDownloaded[task.id] ?: currentBytes
                        val speed = (currentBytes - prev).coerceAtLeast(0L)
                        previousDownloaded[task.id] = currentBytes
                        task.downloadedBytes = currentBytes
                        task.speedBytesPerSec = speed
                        totalSpeed += speed
                        notifyTaskUpdated(task)
                    } else {
                        previousDownloaded.remove(task.id)
                        if (task.speedBytesPerSec > 0L) {
                            task.speedBytesPerSec = 0L
                            notifyTaskUpdated(task)
                        }
                    }
                }

                notifySpeedUpdated(totalSpeed, activeCount)
            }
        }
    }

    fun getDownloadFolder(context: Context): File {
        if (!settings.customStoragePath.isNullOrEmpty()) {
            val custom = File(settings.customStoragePath!!)
            if (isUsable(custom)) return custom
        }

        // Scoped storage: from Android 10 the public Downloads folder is off limits without
        // a permission that no longer exists for us, and some ROMs still report canWrite()
        // as true there, so writes fail later with EACCES instead of falling back here.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Filigram")
            publicDir.mkdirs()
            if (isUsable(publicDir)) return publicDir
        }

        val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Filigram")
        appDir.mkdirs()
        if (isUsable(appDir)) return appDir

        return File(context.filesDir, "Downloads").apply { mkdirs() }
    }

    /** canWrite() lies on scoped-storage paths, so prove it by creating a real file. */
    private fun isUsable(dir: File): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        return try {
            val probe = File(dir, ".filigram_write_test")
            probe.outputStream().use { it.write(0) }
            probe.delete()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "پوشه ${dir.absolutePath} قابل نوشتن نیست: ${e.message}")
            false
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "۰ KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB/s", mb)
        } else {
            String.format(java.util.Locale.US, "%.0f KB/s", kb)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "۰ MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }
    }
}
