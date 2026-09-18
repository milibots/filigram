package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NextMovieApi {

    private const val TAG = "NextMovieApi"
    private const val KEY = "nextmovie"
    private const val BASE_URL = "https://mihan-cdn.com"
    private const val AUTH_TOKEN = "j1LG8eYNnk0EBzTCRcXyo6kebJrnX6EQx6zsmFKv7e5077d7"
    private const val PLATFORM = "android/6.4"
    private const val USER_AGENT = "okhttp/5.5.0"

    private val seriesCache = mutableMapOf<Int, Map<Int, List<EpisodeItem>>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun base(): String = SourceConfig.baseUrl(KEY, BASE_URL)

    private fun buildRequest(url: String, method: String = "GET", jsonBody: String? = null): Request {
        val fallbackHeaders = mapOf(
            "Authorization" to "Bearer $AUTH_TOKEN",
            "Platform" to PLATFORM,
            "User-Agent" to USER_AGENT,
            "Content-Type" to "application/json; charset=UTF-8",
            "Accept" to "application/json"
        )
        val builder = Request.Builder().url(url)
        for ((k, v) in SourceConfig.headers(KEY, fallbackHeaders)) builder.header(k, v)

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> {
                val body = (jsonBody ?: "{}").toRequestBody(mediaType)
                builder.post(body)
            }
        }
        return builder.build()
    }

    private fun execute(url: String, method: String = "GET", jsonBody: String? = null): Pair<Int, String> {
        // Download/streaming links must ALWAYS be fetched fresh — never cache /link endpoints
        val isCacheable = method.equals("GET", ignoreCase = true) &&
                (url.contains("/details") || url.contains("/season")) &&
                !url.contains("/link")
        val cacheKey = "nextmovie_${url.substringAfter("https://mihan-cdn.com/")}"

        if (isCacheable) {
            val cached = AppCacheManager.get(cacheKey)
            if (!cached.isNullOrEmpty()) {
                AppLogger.s(TAG, "⚡ پاسخ سریع از کش هوشمند نکست‌مووی: $url")
                return Pair(200, cached)
            }
        }

        return try {
            val req = buildRequest(url, method, jsonBody)
            val res = client.newCall(req).execute()
            val code = res.code
            val body = res.body?.string() ?: ""

            if (isCacheable && code == 200 && body.isNotEmpty()) {
                AppCacheManager.put(cacheKey, body)
            }

            Pair(code, body)
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در درخواست به $url: ${e.message}")
            Pair(-1, "")
        }
    }

    private fun parseMovieItem(it: JSONObject): MovieItem {
        val id = it.optInt("id")
        val title = it.optString("title", "بدون عنوان")
        val poster = it.optString("poster", "")
        val typeStr = it.optString("type", "movie")
        val isSeries = typeStr == "series" || typeStr == "tvshow"
        val year = it.opt("year")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val isDubbed = it.optBoolean("is_dubbed", false)
        val hasSub = it.optString("sub_status", "") == "sub"

        return MovieItem(
            id = id,
            title = title,
            image = poster,
            type = if (isSeries) 1 else 0,
            year = year,
            hasSub = hasSub,
            hasDub = isDubbed,
            slug = "next-$id"
        )
    }

    suspend fun getRecent(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/api/v3/search?page=$page"
        val payload = JSONObject().apply {
            put("type", "all")
        }.toString()

        val (code, res) = execute(url, method = "POST", jsonBody = payload)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONObject("data")
                val items = data?.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        list.add(parseMovieItem(items.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در پردازش لیست فیلم‌ها: ${e.message}")
            }
        }
        AppLogger.s(TAG, "تعداد ${list.size} عنوان از نکست‌مووی (صفحه $page) دریافت شد.")
        list
    }

    suspend fun search(query: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/api/v3/search?page=$page"
        val payload = JSONObject().apply {
            put("title", query)
        }.toString()

        val (code, res) = execute(url, method = "POST", jsonBody = payload)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONObject("data")
                val items = data?.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        list.add(parseMovieItem(items.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در پردازش جستجو: ${e.message}")
            }
        }
        AppLogger.s(TAG, "تعداد ${list.size} نتیجه برای '$query' از نکست‌مووی دریافت شد.")
        list
    }

    suspend fun getDetails(movieId: Int): MovieDetail? = withContext(Dispatchers.IO) {
        val detailsUrl = "${base()}/api/movie/$movieId/details"
        val (code, res) = execute(detailsUrl)
        if (code != 200) return@withContext null

        try {
            val root = JSONObject(res)
            val d = root.optJSONObject("data") ?: return@withContext null

            val id = d.optInt("id", movieId)
            val title = d.optString("title", "بدون عنوان")
            val poster = d.optString("poster", "")
            val description = d.optString("description", "خلاصه داستانی ثبت نشده است.")
            val year = d.opt("year")?.toString()?.takeIf { it != "null" }
            val imdbRating = d.optString("imdb_rating").takeIf { it.isNotBlank() && it != "null" }
            val duration = d.opt("duration")?.toString()?.let { "$it دقیقه" }
            val typeStr = d.optString("type", "movie")
            val isSeries = typeStr == "series" || typeStr == "tvshow"

            val (qualities, seasonsList) = fetchLinksAndSeasons(movieId)

            return@withContext MovieDetail(
                id = id,
                title = title,
                image = poster,
                banner = poster,
                type = if (isSeries) 1 else 0,
                imdbRate = imdbRating,
                duration = duration,
                year = year,
                description = description,
                descriptionAi = null,
                seasons = seasonsList,
                directQualities = qualities,
                slug = "next-$id"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت جزییات عنوان $movieId: ${e.message}")
            null
        }
    }

    private fun fetchLinksAndSeasons(movieId: Int): Pair<List<QualityItem>, List<SeasonItem>> {
        val linksUrl = "${base()}/api/movie/$movieId/links"
        val (code, res) = execute(linksUrl)
        if (code != 200) return Pair(emptyList(), emptyList())

        try {
            val root = JSONObject(res)
            val data = root.optJSONObject("data") ?: return Pair(emptyList(), emptyList())

            val qualities = mutableListOf<QualityItem>()
            val seasonsList = mutableListOf<SeasonItem>()

            val movieLinks = data.optJSONArray("movie_links")
            if (movieLinks != null) {
                for (i in 0 until movieLinks.length()) {
                    val linkObj = movieLinks.getJSONObject(i)
                    val qId = linkObj.optInt("id", i + 1)
                    val quality = linkObj.optString("quality", "HD").trim()
                    val type = linkObj.optString("type", "mp4").uppercase()
                    val url = linkObj.optString("url")

                    if (url.isNotBlank()) {
                        qualities.add(
                            QualityItem(
                                id = qId,
                                type = type,
                                title = "$quality ($type)",
                                size = "مستقیم",
                                directUrl = url
                            )
                        )
                    }
                }
            }

            val seasonsArr = data.optJSONArray("seasons")
            if (seasonsArr != null && seasonsArr.length() > 0) {
                val epMap = mutableMapOf<Int, List<EpisodeItem>>()

                for (s in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.getJSONObject(s)
                    val sOrder = s + 1
                    val sTitle = sObj.optString("title", "فصل $sOrder")
                    seasonsList.add(SeasonItem(season = sOrder, title = sTitle))

                    val epArr = sObj.optJSONArray("episodes")
                    val episodesList = mutableListOf<EpisodeItem>()

                    if (epArr != null) {
                        for (e in 0 until epArr.length()) {
                            val epObj = epArr.getJSONObject(e)
                            val epOrder = e + 1
                            val epTitle = epObj.optString("title", "قسمت $epOrder")
                            val epLinksArr = epObj.optJSONArray("links")
                            val epQualities = mutableListOf<QualityItem>()

                            if (epLinksArr != null) {
                                for (l in 0 until epLinksArr.length()) {
                                    val lObj = epLinksArr.getJSONObject(l)
                                    val lId = lObj.optInt("id", (epOrder * 100) + l)
                                    val lQuality = lObj.optString("quality", "کیفیت اصلی")
                                    val lType = lObj.optString("type", "mp4").uppercase()
                                    val lUrl = lObj.optString("url")

                                    if (lUrl.isNotBlank()) {
                                        epQualities.add(
                                            QualityItem(
                                                id = lId,
                                                type = lType,
                                                title = "$lQuality ($lType)",
                                                size = "مستقیم",
                                                directUrl = lUrl
                                            )
                                        )
                                    }
                                }
                            }

                            episodesList.add(
                                EpisodeItem(
                                    episode = epOrder,
                                    title = epTitle,
                                    qualities = epQualities
                                )
                            )
                        }
                    }
                    epMap[sOrder] = episodesList
                }
                seriesCache[movieId] = epMap
            }

            return Pair(qualities, seasonsList)
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در پردازش لینک‌های نکست‌مووی: ${e.message}")
            return Pair(emptyList(), emptyList())
        }
    }

    suspend fun getEpisodes(movieId: Int, seasonNumber: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val cached = seriesCache[movieId]?.get(seasonNumber)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        fetchLinksAndSeasons(movieId)
        seriesCache[movieId]?.get(seasonNumber) ?: emptyList()
    }

    suspend fun getQualities(movieId: Int): List<QualityItem> = withContext(Dispatchers.IO) {
        val (qualities, _) = fetchLinksAndSeasons(movieId)
        qualities
    }

    suspend fun getProfile(): JSONObject? = withContext(Dispatchers.IO) {
        val (code, res) = execute("${base()}/api/me")
        if (code == 200) {
            try {
                return@withContext JSONObject(res).optJSONObject("data")
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در دریافت پروفایل نکست‌مووی: ${e.message}")
            }
        }
        null
    }
}
