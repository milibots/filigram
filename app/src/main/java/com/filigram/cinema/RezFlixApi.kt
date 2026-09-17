package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RezFlixApi {
    private const val BASE_URL = "http://server-win-iran.info"
    private const val TOKEN = "4F5A9C3D9A86FA54EACEDDD635185"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun getMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/movie/by/filtres/0/created/0/$TOKEN/?page=$page"
        val req = Request.Builder().url(url).header("User-Agent", "okhttp/4.12.0").build()
        val items = mutableListOf<MovieItem>()
        try {
            val res = client.newCall(req).execute()
            val text = res.body?.string() ?: "[]"
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val isSeries = "serie".equals(it.optString("type"), ignoreCase = true)
                items.add(MovieItem(
                    id = it.optInt("id"),
                    title = it.optString("title"),
                    image = it.optString("image"),
                    type = if (isSeries) 1 else 0,
                    year = if (it.has("year")) it.optString("year") else null
                ))
            }
            AppLogger.s("RezFlix", "دریافت ${items.size} فیلم از موتور RezFlix")
        } catch (e: Exception) {
            AppLogger.e("RezFlix", "خطا در اتصال به موتور RezFlix: ${e.message}")
        }
        items
    }

    suspend fun search(q: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api-user/newapi/like.php?action=search-movie&q=$q&pageno=$page"
        val req = Request.Builder().url(url).header("User-Agent", "okhttp/4.12.0").build()
        val items = mutableListOf<MovieItem>()
        try {
            val res = client.newCall(req).execute()
            val text = res.body?.string() ?: "[]"
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val isSeries = "serie".equals(it.optString("type"), ignoreCase = true)
                items.add(MovieItem(
                    id = it.optInt("id"),
                    title = it.optString("title"),
                    image = it.optString("image"),
                    type = if (isSeries) 1 else 0,
                    year = if (it.has("year")) it.optString("year") else null
                ))
            }
        } catch (e: Exception) {
            AppLogger.e("RezFlix", "خطا در جستجوی RezFlix: ${e.message}")
        }
        items
    }

    suspend fun getDetails(id: Int): MovieDetail? = withContext(Dispatchers.IO) {
        val cacheKey = "rezflix_detail_$id"
        val cached = AppCacheManager.get(cacheKey)
        if (!cached.isNullOrEmpty()) {
            try {
                val obj = JSONObject(cached)
                val isSeries = "serie".equals(obj.optString("type"), ignoreCase = true)
                AppLogger.s("RezFlix", "⚡ جزییات از کش هوشمند RezFlix بازیابی شد: $id")
                return@withContext MovieDetail(
                    id = id,
                    title = obj.optString("title"),
                    image = obj.optString("image"),
                    banner = obj.optString("cover", obj.optString("image")),
                    type = if (isSeries) 1 else 0,
                    imdbRate = if (obj.has("imdb")) obj.optString("imdb") else null,
                    duration = obj.optString("duration", ""),
                    year = if (obj.has("year")) obj.optString("year") else null,
                    description = obj.optString("description", ""),
                    descriptionAi = null,
                    seasons = if (isSeries) listOf(SeasonItem(1, "فصل ۱")) else emptyList()
                )
            } catch (_: Exception) {}
        }

        val url = "$BASE_URL/api/movie/by/$id/$TOKEN/"
        val req = Request.Builder().url(url).header("User-Agent", "okhttp/4.12.0").build()
        try {
            val res = client.newCall(req).execute()
            val text = res.body?.string() ?: "{}"
            if (res.isSuccessful && text.length > 5) {
                AppCacheManager.put(cacheKey, text)
            }
            val obj = JSONObject(text)
            val isSeries = "serie".equals(obj.optString("type"), ignoreCase = true)

            MovieDetail(
                id = id,
                title = obj.optString("title"),
                image = obj.optString("image"),
                banner = obj.optString("cover", obj.optString("image")),
                type = if (isSeries) 1 else 0,
                imdbRate = if (obj.has("imdb")) obj.optString("imdb") else null,
                duration = obj.optString("duration", ""),
                year = if (obj.has("year")) obj.optString("year") else null,
                description = obj.optString("description", ""),
                descriptionAi = null,
                seasons = if (isSeries) listOf(SeasonItem(1, "فصل ۱")) else emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
}
