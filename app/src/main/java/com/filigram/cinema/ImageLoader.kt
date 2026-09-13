package com.filigram.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

object ImageLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var diskCacheDir: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        try {
            val dir = File(context.cacheDir, "img_disk_cache")
            if (!dir.exists()) dir.mkdirs()
            diskCacheDir = dir
        } catch (_: Exception) {}
    }

    private fun hashKey(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }

    fun load(url: String?, imageView: ImageView, placeholderRes: Int = R.drawable.placeholder_poster) {
        if (url.isNullOrEmpty()) {
            imageView.setImageResource(placeholderRes)
            return
        }

        val resolvedUrl = when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            else -> "https://content.expertapp.org" + if (url.startsWith("/")) url else "/$url"
        }

        // 1. Instant Memory Cache (0 ms)
        val cached = memoryCache.get(resolvedUrl)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageResource(placeholderRes)
        imageView.tag = resolvedUrl

        scope.launch {
            try {
                val diskFile = diskCacheDir?.let { File(it, hashKey(resolvedUrl)) }

                // 2. Persistent Disk Cache (Loads locally without consuming internet)
                if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
                    val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (diskBitmap != null) {
                        memoryCache.put(resolvedUrl, diskBitmap)
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == resolvedUrl) {
                                imageView.setImageBitmap(diskBitmap)
                            }
                        }
                        return@launch
                    }
                }

                // 3. Network Fetch & Save to Local Disk Cache
                val connection = URL(resolvedUrl).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                val input = connection.getInputStream()
                val bytes = input.readBytes()
                input.close()

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    memoryCache.put(resolvedUrl, bitmap)
                    diskFile?.let { f ->
                        try {
                            val fos = FileOutputStream(f)
                            fos.write(bytes)
                            fos.close()
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        if (imageView.tag == resolvedUrl) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("ImageLoader", "خطا در دانلود تصویر: $resolvedUrl - ${e.message}")
            }
        }
    }
}
