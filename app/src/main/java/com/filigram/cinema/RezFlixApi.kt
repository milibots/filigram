package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RezFlixApi {
    private const val KEY = "rezflix"
    private const val BASE_URL = "http://server-win-iran.info"
    private const val TOKEN = "4F5A9C3D9A86FA54EACEDDD635185"
    private val FALLBACK_HEADERS = mapOf("User-Agent" to "okhttp/4.12.0")

    private fun base(): String = SourceConfig.baseUrl(KEY, BASE_URL)

    private fun token(): String = SourceConfig.auth(KEY)["token"] ?: TOKEN

    private fun newRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        for ((k, v) in SourceConfig.headers(KEY, FALLBACK_HEADERS)) builder.header(k, v)
        return builder.build()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun getMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val url = "${base()}/api/movie/by/filtres/0/created/0/${token()}/?page=$page"
        val req = newRequest(url)
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
        val url = "${base()}/api-user/newapi/like.php?action=search-movie&q=$q&pageno=$page"
        val req = newRequest(url)
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

        val url = "${base()}/api/movie/by/$id/${token()}/"
        val req = newRequest(url)
        try {
            val res = client.newCall(req).execute()
            val text = res.body?.string() ?: "{}"
            if (res.isSuccessful && text.length > 5) {
                AppCacheManager.put(cacheKey, text)
            }
            val obj = JSONObject(text)
            val isSeries = "serie".equals(obj.optString("type"), ignoreCase = true)
            val realSeasons = if (isSeries) getSeasons(id) else emptyList()

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
                seasons = if (isSeries) realSeasons.ifEmpty { listOf(SeasonItem(1, "فصل ۱")) } else emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private val episodeTag = Regex("""S(\d{1,2})[\s._-]*E(\d{1,3})""", RegexOption.IGNORE_CASE)

    /** Raw file list of an item. Series files are flat; season/episode only live in the file name (S01E02). */
    private suspend fun fetchSources(id: Int): List<QualityItem> = withContext(Dispatchers.IO) {
        try {
            val res = client.newCall(newRequest("${base()}/api/movie/by/$id/${token()}/")).execute()
            val obj = JSONObject(res.body?.string() ?: "{}")
            val arr = obj.optJSONArray("sources") ?: JSONArray()
            val out = mutableListOf<QualityItem>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val url = s.optString("url").ifBlank { s.optString("link").ifBlank { s.optString("file") } }
                if (url.isBlank() || Regex("trailer|teaser", RegexOption.IGNORE_CASE).containsMatchIn(url)) continue
                out.add(
                    QualityItem(
                        id = i + 1,
                        type = s.optString("type", "MP4").uppercase(),
                        title = s.optString("label").ifBlank { s.optString("quality").ifBlank { "کیفیت اصلی" } },
                        size = "مستقیم",
                        directUrl = url
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun tagOf(q: QualityItem): Pair<Int, Int>? {
        val decoded = try { java.net.URLDecoder.decode(q.directUrl.orEmpty().replace("+", "%2B"), "UTF-8") } catch (e: Exception) { q.directUrl.orEmpty() }
        val m = episodeTag.find(decoded) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    suspend fun getQualities(id: Int): List<QualityItem> = fetchSources(id)

    suspend fun getSeasons(id: Int): List<SeasonItem> =
        fetchSources(id).mapNotNull { tagOf(it)?.first }.distinct().sorted().map { SeasonItem(it, "فصل $it") }

    suspend fun getEpisodes(id: Int, season: Int): List<EpisodeItem> =
        fetchSources(id)
            .mapNotNull { q -> tagOf(q)?.takeIf { it.first == season }?.let { it.second to q } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (ep, qs) -> EpisodeItem(ep, "قسمت $ep", qs) }
}
