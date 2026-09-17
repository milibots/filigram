package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AlmasMovieApi {

    private const val TAG = "AlmasMovieApi"
    private const val BASE_URL = "https://almasandroid.com/api/almas/v1"

    private var accessToken = "6X96p0fIP2Zzt2_9evF4lnKzaXtFsy_Axm4yLH5yHHRgHc9QEOV3fwErdb8uey_R"
    private var refreshToken = "mIkn5vbe34uI3B5-XH3Yiql7MZ0hJrxmmsiibUN961qI0fPdar-jcc1V68OGp8zG"
    private const val DEVICE_ID = "00d04fe2-dd3b-4d14-b2ba-88469cb8a01f"
    private const val DEVICE_FINGERPRINT = "06073e34483010815f52e0b9cc1bb0378a2592cb8d4163759f51f513ab74f972"

    private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 9; G576D Build/PQ3B.190801.04221524)"
    private const val APP_VERSION_CODE = "8"
    private const val APP_VERSION_NAME = "3.0.0"

    private val refreshMutex = Mutex()

    private val seriesCache = mutableMapOf<Int, Map<Int, List<EpisodeItem>>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildHeaders(includeAuth: Boolean = true): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json; charset=utf-8",
            "User-Agent" to USER_AGENT,
            "X-Almas-App-Version-Code" to APP_VERSION_CODE,
            "X-Almas-App-Version-Name" to APP_VERSION_NAME,
            "X-Almas-Client" to "android",
            "X-Almas-Device-Fingerprint-Hash" to DEVICE_FINGERPRINT,
            "X-Almas-Device-Id" to DEVICE_ID,
            "X-Almas-Device-Manufacturer" to "Google Phone",
            "X-Almas-Device-Model" to "G576D",
            "X-Almas-Device-Name" to "Google Phone G576D",
            "X-Almas-OS-Version" to "9",
            "X-Almas-Platform" to "android_mobile"
        )
        if (includeAuth && accessToken.isNotBlank()) {
            headers["Authorization"] = "Bearer $accessToken"
        }
        return headers
    }

    private fun executeRequest(
        url: String,
        method: String = "GET",
        jsonBody: String? = null,
        includeAuth: Boolean = true,
        canRetryOn401: Boolean = true
    ): Pair<Int, String> {
        val reqBuilder = Request.Builder().url(url)
        val headers = buildHeaders(includeAuth)
        for ((k, v) in headers) {
            reqBuilder.header(k, v)
        }

        val body = jsonBody?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(body ?: "".toRequestBody("application/json".toMediaType()))
            "PUT" -> reqBuilder.put(body ?: "".toRequestBody("application/json".toMediaType()))
            "DELETE" -> reqBuilder.delete(body)
        }

        val isCacheable = method.equals("GET", ignoreCase = true) &&
                (url.contains("/posts/") || url.contains("/downloads/"))
        val cacheKey = "almas_${url.substringAfter("/api/almas/v1/")}"

        if (isCacheable) {
            val cached = AppCacheManager.get(cacheKey)
            if (!cached.isNullOrEmpty()) {
                AppLogger.s(TAG, "⚡ پاسخ سریع از کش هوشمند الماس‌مووی: $url")
                return Pair(200, cached)
            }
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val code = response.code
        val responseText = response.body?.string() ?: ""

        if (code == 401 && canRetryOn401) {
            AppLogger.w(TAG, "کد ۴۰۱ (انقضای توکن). تلاش برای رفرش خودکار توکن...")
            val refreshed = refreshAccessTokenSync()
            if (refreshed) {
                return executeRequest(url, method, jsonBody, includeAuth, canRetryOn401 = false)
            }
        }

        if (isCacheable && code == 200 && responseText.isNotEmpty()) {
            AppCacheManager.put(cacheKey, responseText)
        }

        return Pair(code, responseText)
    }

    fun refreshAccessTokenSync(): Boolean {
        try {
            val url = "$BASE_URL/auth/refresh/"
            val jsonPayload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }.toString()

            val reqBuilder = Request.Builder().url(url)
            val headers = buildHeaders(includeAuth = false)
            for ((k, v) in headers) {
                reqBuilder.header(k, v)
            }
            reqBuilder.post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))

            val res = client.newCall(reqBuilder.build()).execute()
            val resText = res.body?.string() ?: ""
            if (res.isSuccessful) {
                val root = JSONObject(resText)
                if (root.optBoolean("success", false)) {
                    val tokens = root.optJSONObject("data")?.optJSONObject("tokens")
                    val newAccess = tokens?.optString("access_token")
                    val newRefresh = tokens?.optString("refresh_token")
                    if (!newAccess.isNullOrEmpty()) {
                        accessToken = newAccess
                        if (!newRefresh.isNullOrEmpty()) {
                            refreshToken = newRefresh
                        }
                        AppLogger.s(TAG, "توکن دسترسی الماس‌مووی با موفقیت تجدید شد.")
                        return true
                    }
                }
            }
            AppLogger.e(TAG, "خطا در تجدید توکن: $resText")
        } catch (e: Exception) {
            AppLogger.e(TAG, "استثنا در تجدید توکن: ${e.message}")
        }
        return false
    }

    suspend fun refreshTokenAsync(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            refreshAccessTokenSync()
        }
    }

    suspend fun getConfig(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/config/"
            val (code, res) = executeRequest(url, includeAuth = false)
            if (code == 200) {
                return@withContext JSONObject(res)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت کانفیگ: ${e.message}")
        }
        null
    }

    suspend fun login(phone: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/auth/login/"
            val deviceObj = JSONObject().apply {
                put("device_id", DEVICE_ID)
                put("id", DEVICE_ID)
                put("name", "Google Phone G576D")
                put("manufacturer", "Google Phone")
                put("brand", "Google Phone")
                put("model", "G576D")
            }
            val payload = JSONObject().apply {
                put("phone", phone)
                put("password", pass)
                put("device", deviceObj)
            }.toString()

            val (code, res) = executeRequest(url, method = "POST", jsonBody = payload, includeAuth = false)
            if (code == 200) {
                val root = JSONObject(res)
                if (root.optBoolean("success", false)) {
                    val tokens = root.optJSONObject("data")?.optJSONObject("tokens")
                    accessToken = tokens?.optString("access_token", accessToken) ?: accessToken
                    refreshToken = tokens?.optString("refresh_token", refreshToken) ?: refreshToken
                    AppLogger.s(TAG, "ورود با موفقیت انجام شد.")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در لاگین: ${e.message}")
        }
        false
    }

    suspend fun getProfile(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                if (root.optBoolean("success", false)) {
                    return@withContext root.optJSONObject("data")?.optJSONObject("member")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت پروفایل: ${e.message}")
        }
        null
    }

    private fun parseMovieItem(it: JSONObject): MovieItem {
        val id = it.optInt("id")
        val typeStr = it.optString("type", "movie")
        val isSeries = typeStr == "tvshow" || typeStr == "series"

        val titleObj = it.optJSONObject("title")
        val title = titleObj?.optString("farsi")?.takeIf { it.isNotBlank() }
            ?: titleObj?.optString("english")?.takeIf { it.isNotBlank() }
            ?: titleObj?.optString("display")?.takeIf { it.isNotBlank() }
            ?: it.optString("title", "بدون عنوان")

        val posterObj = it.optJSONObject("poster")
        val poster = posterObj?.optString("thumb220330")?.takeIf { it.isNotBlank() }
            ?: posterObj?.optString("full")?.takeIf { it.isNotBlank() }
            ?: posterObj?.optString("thumbnail", "")
            ?: ""

        var yearStr = it.optString("year").takeIf { it.isNotBlank() }
        if (yearStr == null) {
            val facts = it.optJSONArray("facts")
            if (facts != null) {
                for (j in 0 until facts.length()) {
                    val f = facts.optJSONObject(j)
                    if (f?.optString("id") == "year") {
                        yearStr = f.optString("value")
                        break
                    }
                }
            }
        }

        val badges = it.optJSONObject("badges")
        val hasSub = badges?.optBoolean("has_subtitle", false) ?: false

        return MovieItem(
            id = id,
            title = title,
            image = poster,
            type = if (isSeries) 1 else 0,
            year = yearStr,
            hasSub = hasSub,
            hasDub = false,
            slug = it.optString("slug", "$id")
        )
    }

    suspend fun getHomeSections(): List<VitrinSection> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VitrinSection>()
        try {
            val url = "$BASE_URL/home/"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                val sections = root.optJSONObject("data")?.optJSONArray("sections")
                if (sections != null) {
                    for (i in 0 until sections.length()) {
                        val secObj = sections.getJSONObject(i)
                        val secId = secObj.optString("id")
                        val secTitle = secObj.optString("title").trim()
                        val itemsArr = secObj.optJSONArray("items")

                        if (itemsArr != null && itemsArr.length() > 0 && secTitle.isNotBlank()) {
                            val itemsList = mutableListOf<MovieItem>()
                            for (j in 0 until itemsArr.length()) {
                                val it = itemsArr.getJSONObject(j)
                                itemsList.add(parseMovieItem(it))
                            }
                            if (itemsList.isNotEmpty()) {
                                result.add(VitrinSection(i + 1, secTitle, itemsList))
                            }
                        }
                    }
                }
            }
            AppLogger.s(TAG, "تعداد ${result.size} بخش ویترین از الماس‌مووی دریافت شد.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت بخش‌های خانه: ${e.message}")
        }
        result
    }

    suspend fun getRecent(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        getSectionPosts(sectionId = "new_movie", page = page, perPage = 24)
    }

    suspend fun getSectionPosts(
        sectionId: String = "new_movie",
        page: Int = 1,
        perPage: Int = 24
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val url = "$BASE_URL/post-list/?section_id=$sectionId&source=section_query&sort=default&page=$page&per_page=$perPage"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                val itemsArr = root.optJSONObject("data")?.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        list.add(parseMovieItem(itemsArr.getJSONObject(i)))
                    }
                }
            }
            AppLogger.s(TAG, "تعداد ${list.size} عنوان از بخش '$sectionId' الماس‌مووی دریافت شد.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت لیست بخش $sectionId: ${e.message}")
        }
        list
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
        type: String = "all",
        sort: String = "relevance"
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/?q=$encodedQuery&type=$type&sort=$sort&page=$page&per_page=$perPage"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                val itemsArr = root.optJSONObject("data")?.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        list.add(parseMovieItem(itemsArr.getJSONObject(i)))
                    }
                }
            }
            AppLogger.s(TAG, "تعداد ${list.size} نتیجه برای '$query' از الماس‌مووی دریافت شد.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در جستجو: ${e.message}")
        }
        list
    }

    suspend fun getDetails(postId: Int, mediaType: String = "movie"): MovieDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/posts/$postId/"
            val (code, res) = executeRequest(url)
            if (code != 200) {
                AppLogger.w(TAG, "خطا در دریافت جزییات کد: $code")
                return@withContext null
            }

            val root = JSONObject(res)
            val data = root.optJSONObject("data") ?: return@withContext null

            val typeStr = data.optString("type", mediaType)
            val isSeries = typeStr == "tvshow" || typeStr == "series" || mediaType == "tvshow"

            val titleObj = data.optJSONObject("title")
            val title = titleObj?.optString("farsi")?.takeIf { it.isNotBlank() }
                ?: titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                ?: titleObj?.optString("display")
                ?: data.optString("wordpress_title", "عنوان نامشخص")

            val posterObj = data.optJSONObject("poster") ?: data.optJSONObject("media")?.optJSONObject("poster")
            val image = posterObj?.optString("full")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("thumb220330")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("thumbnail", "") ?: ""

            val backdropObj = data.optJSONObject("backdrop") ?: data.optJSONObject("media")?.optJSONObject("backdrop")
            val banner = backdropObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: backdropObj?.optString("full")?.takeIf { it.isNotBlank() }
                ?: image

            val ratingsObj = data.optJSONObject("ratings")
            val imdbRate = ratingsObj?.optString("imdb")?.takeIf { it.isNotBlank() }

            val sumObj = data.optJSONObject("summaries")
            val description = sumObj?.optString("display")?.takeIf { it.isNotBlank() }
                ?: sumObj?.optString("farsi")?.takeIf { it.isNotBlank() }
                ?: sumObj?.optString("english")?.takeIf { it.isNotBlank() }

            var yearStr: String? = null
            var durationStr: String? = null
            val factsArr = data.optJSONArray("facts")
            if (factsArr != null) {
                for (i in 0 until factsArr.length()) {
                    val f = factsArr.optJSONObject(i) ?: continue
                    val fid = f.optString("id")
                    if (fid == "year") yearStr = f.optString("value")
                    if (fid == "runtime") durationStr = f.optString("value")
                }
            }

            val downloads = fetchDownloads(postId)

            var seasonsList = emptyList<SeasonItem>()
            var directQualities = emptyList<QualityItem>()

            if (isSeries) {
                seasonsList = downloads.seasons
                seriesCache[postId] = downloads.episodesBySeason
            } else {
                directQualities = downloads.directQualities
            }

            return@withContext MovieDetail(
                id = postId,
                title = title,
                image = image,
                banner = banner,
                type = if (isSeries) 1 else 0,
                imdbRate = imdbRate,
                duration = durationStr,
                year = yearStr,
                description = description,
                descriptionAi = null,
                seasons = seasonsList,
                directQualities = directQualities,
                slug = data.optString("slug", "$postId")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "استثنا در دریافت جزییات پست $postId: ${e.message}")
        }
        null
    }

    private data class ParsedDownloads(
        val directQualities: List<QualityItem> = emptyList(),
        val seasons: List<SeasonItem> = emptyList(),
        val episodesBySeason: Map<Int, List<EpisodeItem>> = emptyMap()
    )

    private fun fetchDownloads(postId: Int): ParsedDownloads {
        try {
            val url = "$BASE_URL/posts/$postId/downloads/?include_locked=1"
            val (code, res) = executeRequest(url)
            if (code != 200) return ParsedDownloads()

            val root = JSONObject(res)
            val dData = root.optJSONObject("data")?.optJSONObject("downloads") ?: return ParsedDownloads()

            if (dData.has("items")) {
                val itemsArr = dData.optJSONArray("items")
                val qList = mutableListOf<QualityItem>()
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val item = itemsArr.getJSONObject(i)
                        val label = item.optString("label", "کیفیت اصلی")
                        val size = item.optString("size", "")
                        val dlUrl = item.optString("url")
                        val resolution = item.optString("resolution", "1080p")

                        qList.add(
                            QualityItem(
                                id = (dlUrl.hashCode() and 0x7FFFFFFF).let { if (it == 0) i + 1 else it },
                                type = resolution,
                                title = label,
                                size = size,
                                directUrl = dlUrl
                            )
                        )
                    }
                }
                return ParsedDownloads(directQualities = qList)
            }

            if (dData.has("seasons")) {
                val seasonsArr = dData.optJSONArray("seasons")
                val seasonItems = mutableListOf<SeasonItem>()
                val epMap = mutableMapOf<Int, List<EpisodeItem>>()

                if (seasonsArr != null) {
                    for (s in 0 until seasonsArr.length()) {
                        val sObj = seasonsArr.getJSONObject(s)
                        val sOrder = sObj.optInt("season_order", s + 1)
                        val sName = sObj.optString("name", "$sOrder")
                        seasonItems.add(SeasonItem(season = sOrder, title = "فصل $sName"))

                        val qualitiesArr = sObj.optJSONArray("qualities")

                        val episodeQualitiesMap = mutableMapOf<Int, MutableList<QualityItem>>()

                        if (qualitiesArr != null) {
                            for (q in 0 until qualitiesArr.length()) {
                                val qObj = qualitiesArr.getJSONObject(q)
                                val qName = qObj.optString("name", "کیفیت")
                                val episodesArr = qObj.optJSONArray("episodes")

                                if (episodesArr != null) {
                                    for (e in 0 until episodesArr.length()) {
                                        val epObj = episodesArr.getJSONObject(e)
                                        val epOrder = epObj.optInt("episode_order", e + 1)
                                        val epUrl = epObj.optString("download_url")
                                        val epSize = epObj.optString("size", "")

                                        val qualityItem = QualityItem(
                                            id = (epUrl.hashCode() and 0x7FFFFFFF).let { if (it == 0) e + 1 else it },
                                            type = "لینک مستقیم",
                                            title = qName,
                                            size = epSize,
                                            directUrl = epUrl
                                        )

                                        val listForEp = episodeQualitiesMap.getOrPut(epOrder) { mutableListOf() }
                                        listForEp.add(qualityItem)
                                    }
                                }
                            }
                        }

                        val epList = episodeQualitiesMap.entries.sortedBy { it.key }.map { (order, qList) ->
                            EpisodeItem(
                                episode = order,
                                title = "قسمت $order",
                                qualities = qList
                            )
                        }
                        epMap[sOrder] = epList
                    }
                }
                return ParsedDownloads(seasons = seasonItems, episodesBySeason = epMap)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در استخراج دانلودهای پست $postId: ${e.message}")
        }
        return ParsedDownloads()
    }

    suspend fun getEpisodes(postId: Int, seasonNumber: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val cached = seriesCache[postId]?.get(seasonNumber)
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        val downloads = fetchDownloads(postId)
        seriesCache[postId] = downloads.episodesBySeason
        downloads.episodesBySeason[seasonNumber] ?: emptyList()
    }

    suspend fun getQualities(postId: Int): List<QualityItem> = withContext(Dispatchers.IO) {
        val downloads = fetchDownloads(postId)
        downloads.directQualities
    }

    suspend fun getComments(postId: Int, page: Int = 1, perPage: Int = 8): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/posts/$postId/comments/?page=$page&per_page=$perPage"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                return@withContext root.optJSONObject("data")?.optJSONArray("comments")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت دیدگاه‌ها: ${e.message}")
        }
        null
    }

    suspend fun submitComment(
        postId: Int,
        body: String,
        parentId: Int = 0,
        spoiler: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/posts/$postId/comments"
            val payload = JSONObject().apply {
                put("body", body)
                put("parent_id", parentId)
                put("spoiler", spoiler)
            }.toString()

            val (code, res) = executeRequest(url, method = "POST", jsonBody = payload)
            if (code in 200..201) {
                AppLogger.s(TAG, "دیدگاه با موفقیت ثبت شد.")
                return@withContext true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در ارسال دیدگاه: ${e.message}")
        }
        false
    }

    suspend fun getGenres(page: Int = 1, perPage: Int = 50): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/taxonomies/genre/terms/?page=$page&per_page=$perPage&hide_empty=1"
            val (code, res) = executeRequest(url, includeAuth = false)
            if (code == 200) {
                val root = JSONObject(res)
                return@withContext root.optJSONObject("data")?.optJSONArray("terms")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت ژانرها: ${e.message}")
        }
        null
    }

    suspend fun getPostsByTaxonomy(
        taxonomy: String = "genre",
        termSlug: String,
        page: Int = 1,
        perPage: Int = 24,
        sort: String = "modified"
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val url = "$BASE_URL/taxonomies/$taxonomy/terms/$termSlug/posts/?page=$page&per_page=$perPage&sort=$sort"
            val (code, res) = executeRequest(url)
            if (code == 200) {
                val root = JSONObject(res)
                val itemsArr = root.optJSONObject("data")?.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        list.add(parseMovieItem(itemsArr.getJSONObject(i)))
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در دریافت فیلم‌های ژانر $termSlug: ${e.message}")
        }
        list
    }

    suspend fun likePost(postId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/liked/$postId"
            val (code, _) = executeRequest(url, method = "PUT")
            return@withContext code in 200..204
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در لایک پست: ${e.message}")
            false
        }
    }

    suspend fun unlikePost(postId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/liked/$postId"
            val (code, _) = executeRequest(url, method = "DELETE")
            return@withContext code in 200..204
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در آنلایک پست: ${e.message}")
            false
        }
    }

    suspend fun savePost(postId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/saved/$postId"
            val (code, _) = executeRequest(url, method = "PUT")
            return@withContext code in 200..204
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در ذخیره پست: ${e.message}")
            false
        }
    }

    suspend fun unsavePost(postId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/saved/$postId"
            val (code, _) = executeRequest(url, method = "DELETE")
            return@withContext code in 200..204
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در حذف ذخیره پست: ${e.message}")
            false
        }
    }

    suspend fun updatePlaybackProgress(
        postId: Int,
        episodeKey: String = "",
        progressSeconds: Long = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/me/playback-progress/$postId"
            val payload = JSONObject().apply {
                if (episodeKey.isNotBlank()) put("episode_key", episodeKey)
                put("progress_seconds", progressSeconds)
            }.toString()

            val (code, _) = executeRequest(url, method = "PUT", jsonBody = payload)
            return@withContext code in 200..204
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در بروزرسانی پیشرفت پخش: ${e.message}")
            false
        }
    }
}
