package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BjApi {
    private const val TAG = "BjApi"
    private const val KEY = "bj"
    private const val BASE_URL = "https://forooshonline20.ir/wp-json/mapi/v1"
    private val FALLBACK_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    private fun base(): String = SourceConfig.baseUrl(KEY, BASE_URL)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Cache: movieId -> (seasonNumber -> List<EpisodeItem>)
    private val seriesCache = ConcurrentHashMap<Int, Map<Int, List<EpisodeItem>>>()

    private fun execute(url: String): Pair<Int, String> {
        val cacheKey = "bj_${url.substringAfter("wp-json/mapi/v1/")}"
        // Download/streaming links must ALWAYS be fetched fresh —
        // Only cache collection/listing endpoints (movies, series, cartoons, suggestions).
        // Single post detail pages (/post/{id}) embed download_links, so they must never be cached.
        val isCacheable = url.contains("/post/") &&
                !url.contains("/search") &&
                Regex("/post/\\d+").containsMatchIn(url).not()

        if (isCacheable) {
            val cached = AppCacheManager.get(cacheKey)
            if (!cached.isNullOrEmpty()) {
                return Pair(200, cached)
            }
        }

        return try {
            val builder = Request.Builder().url(url)
            for ((k, v) in SourceConfig.headers(KEY, FALLBACK_HEADERS)) builder.header(k, v)
            val req = builder.build()
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

    // The API host answers 403 for its own uploads; the CDN mirror serves the same paths.
    private fun imageUrl(raw: String): String {
        val rewritten = SourceConfig.rewriteImage(KEY, raw)
        if (rewritten != raw) return rewritten
        return raw.replace("https://forooshonline20.ir/wp-content/", "https://seo2024.ir/wp-content/")
    }

    private fun parseMovieItem(it: JSONObject): MovieItem {
        val id = it.optInt("id")
        val faTitle = it.optString("fa_title").trim()
        val enTitle = it.optString("title", "بدون عنوان").trim()
        val displayTitle = if (faTitle.isNotBlank()) faTitle else enTitle
        val thumbnail = imageUrl(it.optString("thumbnail").ifEmpty { it.optString("image", "") })
        val typeStr = it.optString("type", "movie")
        val isSeries = typeStr == "serie" || typeStr == "series" || typeStr == "tvshow"
        val release = it.opt("release")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val isDubbed = it.optBoolean("dubbed", false) || it.optBoolean("has_dubbed", false)
        val isSubbed = it.optBoolean("subtitle", true) || it.optBoolean("has_subtitle", true)
        val imdbRate = it.opt("imdb_rate")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

        return MovieItem(
            id = id,
            title = displayTitle,
            image = thumbnail,
            type = if (isSeries) 1 else 0,
            year = release,
            hasSub = isSubbed,
            hasDub = isDubbed,
            rating = imdbRate,
            slug = "bj-$id"
        )
    }

    suspend fun getRecent(page: Int = 1): List<MovieItem> = getMovies(page)

    suspend fun getMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/post/movies?page=$page&per_page=20"
        val (code, res) = execute(url)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        list.add(parseMovieItem(data.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در پردازش لیست فیلم‌های BJ: ${e.message}")
            }
        }
        AppLogger.s(TAG, "تعداد ${list.size} فیلم از موتور BJ (صفحه $page) دریافت شد.")
        list
    }

    suspend fun getSeries(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/post/series?page=$page&per_page=20"
        val (code, res) = execute(url)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        list.add(parseMovieItem(data.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در پردازش سریال‌های BJ: ${e.message}")
            }
        }
        AppLogger.s(TAG, "تعداد ${list.size} سریال از موتور BJ (صفحه $page) دریافت شد.")
        list
    }

    suspend fun getCartoons(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/post/cartoons?page=$page&per_page=20"
        val (code, res) = execute(url)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        list.add(parseMovieItem(data.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در پردازش کارتون‌های BJ: ${e.message}")
            }
        }
        list
    }

    suspend fun getSuggestions(): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val url = "${base()}/post/suggestions"
        val (code, res) = execute(url)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        list.add(parseMovieItem(data.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در دریافت پیشنهادات BJ: ${e.message}")
            }
        }
        list
    }

    suspend fun search(query: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "${base()}/post/search?search=$encoded&page=$page&per_page=20"
        val (code, res) = execute(url)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        list.add(parseMovieItem(data.getJSONObject(i)))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در جستجوی BJ برای '$query': ${e.message}")
            }
        }
        AppLogger.s(TAG, "تعداد ${list.size} نتیجه برای '$query' از موتور BJ دریافت شد.")
        list
    }

    suspend fun getHomeSections(): List<VitrinSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<VitrinSection>()
        var sectionId = 1

        val suggestions = getSuggestions()
        if (suggestions.isNotEmpty()) {
            sections.add(VitrinSection(sectionId++, "پیشنهادات ویژه (موتور BJ)", suggestions))
        }

        val movies = getMovies(1)
        if (movies.isNotEmpty()) {
            sections.add(VitrinSection(sectionId++, "جدیدترین فیلم‌های روز (موتور BJ)", movies))
        }

        val series = getSeries(1)
        if (series.isNotEmpty()) {
            sections.add(VitrinSection(sectionId++, "برترین سریال‌های روز (موتور BJ)", series))
        }

        val cartoons = getCartoons(1)
        if (cartoons.isNotEmpty()) {
            sections.add(VitrinSection(sectionId++, "انیمیشن و کارتون‌های منتخب (موتور BJ)", cartoons))
        }

        sections
    }

    suspend fun getDetails(id: Int): MovieDetail? = withContext(Dispatchers.IO) {
        val url = "${base()}/post/$id"
        val (code, res) = execute(url)
        if (code != 200) return@withContext null

        try {
            val root = JSONObject(res)
            val d = root.optJSONObject("data") ?: return@withContext null

            val postId = d.optInt("id", id)
            val faTitle = d.optString("fa_title").trim()
            val enTitle = d.optString("title", "بدون عنوان").trim()
            val title = if (faTitle.isNotBlank()) faTitle else enTitle
            val thumbnail = imageUrl(d.optString("thumbnail").ifEmpty { d.optString("image", "") })
            val banner = imageUrl(d.optString("background_image").ifEmpty { d.optString("background", thumbnail) })
            val typeStr = d.optString("type", "movie")
            val isSeries = typeStr == "serie" || typeStr == "series" || typeStr == "tvshow"

            val release = d.opt("release")?.toString()?.takeIf { it != "null" }
            val imdbRate = d.opt("imdb_rate")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val runtime = d.opt("runtime")?.toString()?.let { if (it.isNotBlank() && it != "null") "$it دقیقه" else null }

            val faPlot = d.optString("fa_plot").trim()
            val plot = d.optString("plot").trim()
            val enPlot = d.optString("en_plot").trim()
            val description = when {
                faPlot.isNotBlank() -> faPlot
                plot.isNotBlank() -> plot
                enPlot.isNotBlank() -> enPlot
                else -> "خلاصه داستانی ثبت نشده است."
            }

            val directQualities = mutableListOf<QualityItem>()
            val seasonsList = mutableListOf<SeasonItem>()

            val downloadLinks = d.optJSONArray("download_links")

            if (isSeries) {
                // Parse seasons from download_links
                val seasonNums = mutableSetOf<Int>()
                val epMap = mutableMapOf<Int, MutableMap<Int, MutableList<QualityItem>>>()

                if (downloadLinks != null) {
                    for (i in 0 until downloadLinks.length()) {
                        val dlObj = downloadLinks.getJSONObject(i)
                        val sName = dlObj.optString("name", "")
                        val qualityName = dlObj.optString("quality", "کیفیت اصلی")
                        val linkType = dlObj.optString("type", "").uppercase()

                        val match = Regex("""(?:season|فصل)\s*(\d+)""", RegexOption.IGNORE_CASE).find(sName)
                        val sNum = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                        seasonNums.add(sNum)

                        val itemsArr = dlObj.optJSONArray("items")
                        if (itemsArr != null) {
                            val seasonEpisodes = epMap.getOrPut(sNum) { mutableMapOf() }
                            for (j in 0 until itemsArr.length()) {
                                val itemObj = itemsArr.getJSONObject(j)
                                val epTitle = itemObj.optString("title", "Episode ${j + 1}")
                                val epNum = Regex("""(?:episode|قسمت)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (j + 1)
                                val link = itemObj.optString("link", "")
                                if (link.isNotBlank()) {
                                    val qList = seasonEpisodes.getOrPut(epNum) { mutableListOf() }
                                    qList.add(
                                        QualityItem(
                                            id = (sNum * 1000) + (epNum * 10) + qList.size,
                                            type = if (linkType.isNotBlank()) linkType else "MP4",
                                            title = if (linkType.isNotBlank()) "$qualityName ($linkType)" else qualityName,
                                            size = "مستقیم",
                                            directUrl = link
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                val sortedSeasons = if (seasonNums.isNotEmpty()) seasonNums.sorted() else listOf(1)
                for (s in sortedSeasons) {
                    seasonsList.add(SeasonItem(season = s, title = "فصل $s"))
                }

                // Populate seriesCache
                val finalEpMap = mutableMapOf<Int, List<EpisodeItem>>()
                for ((sNum, epData) in epMap) {
                    finalEpMap[sNum] = epData.keys.sorted().map { epNum ->
                        EpisodeItem(
                            episode = epNum,
                            title = "قسمت $epNum",
                            qualities = epData[epNum] ?: emptyList()
                        )
                    }
                }
                if (finalEpMap.isNotEmpty()) {
                    seriesCache[postId] = finalEpMap
                }
            } else {
                // Parse movie qualities
                if (downloadLinks != null) {
                    for (i in 0 until downloadLinks.length()) {
                        val dlObj = downloadLinks.getJSONObject(i)
                        val dlLink = dlObj.optString("dl_link", "")
                        val quality = dlObj.optString("quality_link", "کیفیت اصلی").trim()
                        val capacity = dlObj.optString("dl_capacity", "").trim()
                        val linkType = dlObj.optString("link_type", "").uppercase()

                        if (dlLink.isNotBlank()) {
                            val titleDisplay = when {
                                linkType.isNotBlank() && capacity.isNotBlank() -> "$quality ($linkType) — $capacity"
                                linkType.isNotBlank() -> "$quality ($linkType)"
                                capacity.isNotBlank() -> "$quality — $capacity"
                                else -> quality
                            }
                            directQualities.add(
                                QualityItem(
                                    id = i + 1,
                                    type = if (linkType.isNotBlank()) linkType else "MP4",
                                    title = titleDisplay,
                                    size = if (capacity.isNotBlank()) capacity else "مستقیم",
                                    directUrl = dlLink
                                )
                            )
                        }
                    }
                }
            }

            return@withContext MovieDetail(
                id = postId,
                title = title,
                image = thumbnail,
                banner = banner,
                type = if (isSeries) 1 else 0,
                imdbRate = imdbRate,
                duration = runtime,
                year = release,
                description = description,
                descriptionAi = null,
                seasons = seasonsList,
                directQualities = directQualities,
                slug = "bj-$postId"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در پردازش جزییات عنوان $id در BJ: ${e.message}")
            null
        }
    }

    suspend fun getEpisodes(movieId: Int, seasonNumber: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = seriesCache[movieId]?.get(seasonNumber)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        // Try dedicated season endpoint: /post/{id}/season/{season}
        val seasonUrl = "${base()}/post/$movieId/season/$seasonNumber"
        val (code, res) = execute(seasonUrl)
        if (code == 200) {
            try {
                val root = JSONObject(res)
                val data = root.optJSONObject("data")
                val downloadLinksObj = data?.optJSONObject("download_links")

                if (downloadLinksObj != null) {
                    val epMap = mutableMapOf<Int, MutableList<QualityItem>>()
                    val keys = downloadLinksObj.keys()

                    while (keys.hasNext()) {
                        val qualityKey = keys.next()
                        val epArray = downloadLinksObj.optJSONArray(qualityKey)
                        if (epArray != null) {
                            for (i in 0 until epArray.length()) {
                                val epObj = epArray.getJSONObject(i)
                                val epTitle = epObj.optString("title", "Episode ${i + 1}")
                                val epNum = Regex("""(?:episode|قسمت)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (i + 1)
                                val link = epObj.optString("link", "")

                                if (link.isNotBlank()) {
                                    val qList = epMap.getOrPut(epNum) { mutableListOf() }
                                    qList.add(
                                        QualityItem(
                                            id = (seasonNumber * 1000) + (epNum * 10) + qList.size,
                                            type = "MP4",
                                            title = qualityKey,
                                            size = "مستقیم",
                                            directUrl = link
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (epMap.isNotEmpty()) {
                        val episodes = epMap.keys.sorted().map { epNum ->
                            EpisodeItem(
                                episode = epNum,
                                title = "قسمت $epNum",
                                qualities = epMap[epNum] ?: emptyList()
                            )
                        }

                        val existing = seriesCache[movieId]?.toMutableMap() ?: mutableMapOf()
                        existing[seasonNumber] = episodes
                        seriesCache[movieId] = existing

                        return@withContext episodes
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "خطا در دریافت فصل $seasonNumber سریال $movieId: ${e.message}")
            }
        }

        // Fallback: call getDetails(movieId) which parses all seasons from main post
        getDetails(movieId)
        seriesCache[movieId]?.get(seasonNumber) ?: emptyList()
    }

    suspend fun getQualities(movieId: Int): List<QualityItem> = withContext(Dispatchers.IO) {
        val detail = getDetails(movieId)
        detail?.directQualities ?: emptyList()
    }
}
