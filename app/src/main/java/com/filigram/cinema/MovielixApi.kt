package com.filigram.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class MovielixApi(private val context: Context) {

    private val TAG = "MovielixApi"
    private var baseUrl = SourceConfig.baseUrl("movielix", "https://expertappmedia.org/api-v1")
    private var token: String? = null
    private val tokenFile = File(context.filesDir, "movielix_token_cache.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun initApi() {
        AppLogger.i(TAG, "مقداردهی اولیه هسته شبکه...")
        loadOrRefreshToken()
    }

    private fun loadOrRefreshToken(): Boolean {
        try {
            if (tokenFile.exists()) {
                val json = JSONObject(tokenFile.readText())
                val expiresAt = json.optLong("expires_at", 0)
                val currentTime = System.currentTimeMillis() / 1000
                if (currentTime < expiresAt) {
                    token = json.optString("token")
                    if (!token.isNullOrEmpty()) {
                        AppLogger.s(TAG, "توکن معتبر از کش بازیابی شد (انقضا: ${(expiresAt - currentTime) / 60} دقیقه دیگر)")
                        return true
                    }
                } else {
                    AppLogger.w(TAG, "توکن موجود در کش منقضی شده است. درخواست دریافت توکن جدید...")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در خواندن فایل کش توکن: ${e.message}")
        }
        return refreshToken()
    }

    @Synchronized
    fun refreshToken(): Boolean {
        AppLogger.i(TAG, "در حال همگام‌سازی آدرس پایه (Base URL)...")
        try {
            val baseUpdate = getUpdatedBaseUrl()
            if (baseUpdate.has("url")) {
                val fetchedUrl = baseUpdate.getString("url").trimEnd('/')
                baseUrl = "$fetchedUrl/api-v1"
                AppLogger.s(TAG, "آدرس پایه دریافت شد: $baseUrl")
            }

            val deviceData = mapOf(
                "apk_code" to "84",
                "apk_name" to "1.8.4",
                "device_version" to "9",
                "device_model" to "G576D",
                "device_brand" to "Google Phone",
                "device_api" to "28",
                "package" to "com.expertapp.movielixmedia",
                "uniq" to "8f66ec3b-a880-4267-b287-74c6f841a2cf",
                "type" to "0",
                "token_firebase" to "cDgipEqUSCq6-zVcj4LEwH:APA91bFsMIW63cV4Hs6AytZ42gsmgRg2YIVp8EA4wpvSqVrWwvrTQUoP6LkEtjNNeBmrdH08601xlIDGJT_RElRVkG8AR78lMPcFQNpozu8DR3aUk-P4q9g",
                "language" to "0",
                "market_type" to "0",
                "user_id" to "0",
                "mcc" to "208",
                "time_zone" to "europe/paris"
            )

            AppLogger.i(TAG, "ارسال امضای دستگاه و دریافت توکن فعال (/device/version)...")
            val versionRes = makeDirectRequest("/device/version", deviceData, false)
            if (versionRes != null && versionRes.optInt("status", 0) == 200 && versionRes.has("token")) {
                token = versionRes.getString("token")
                AppLogger.s(TAG, "توکن جدید با موفقیت فعال شد: $token (شناسه دستگاه: ${versionRes.optString("device_id")})")

                makeDirectRequest("/account/guest", mapOf(
                    "token" to (token ?: ""),
                    "timezone" to "europe/paris",
                    "mcc" to "208"
                ), false)

                makeDirectRequest("/language/set", mapOf(
                    "token" to (token ?: ""),
                    "language" to "1"
                ), false)

                val cache = JSONObject()
                cache.put("token", token)
                cache.put("expires_at", (System.currentTimeMillis() / 1000) + (12 * 3600))
                tokenFile.writeText(cache.toString())
                return true
            } else {
                AppLogger.w(TAG, "پاسخ نامعتبر از سرور ثبت دستگاه. استفاده از توکن اضطراری.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطا در فرآیند فعال‌سازی توکن: ${e.message}")
        }
        token = SourceConfig.auth("movielix")["token"] ?: "178865496596627099"
        return false
    }

    private fun getUpdatedBaseUrl(): JSONObject {
        return try {
            val body = FormBody.Builder().add("movielix", "movielix").build()
            val req = Request.Builder()
                .url("https://global-api2.expertmedias.org/apiMovielix.php")
                .post(body)
                .build()
            val resp = client.newCall(req).execute()
            val bodyStr = getResponseBodyString(resp)
            JSONObject(bodyStr)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun getHeaders(): Map<String, String> {
        val host = try { URI(baseUrl).host ?: "expertappmedia.org" } catch (e: Exception) { "expertappmedia.org" }
        return mapOf(
            "Accept-Encoding" to "gzip",
            "Connection" to "Keep-Alive",
            "Content-Type" to "application/x-www-form-urlencoded",
            "Host" to host,
            "User-Agent" to "okhttp/5.5.0",
            "X-App-Signature" to "AB20bZUC4SQcU/qhMDVfHWX+1liYELEI512YdfJAe+U=",
            "X-Device-Type" to "0",
            "X-Device-Uniq" to "8f66ec3b-a880-4267-b287-74c6f841a2cf",
            "X-Key-Password" to "8edace647f7270c595cdadb594b450d6a2158a91f12aec20ce9b24d5fd59fa39b06537cee4831daf2f8d58d84b",
            "X-Key-Token" to "23606005bfde651bd6118fc638121f99148bd81849d2abff082bf13c998e8ffb794cdf608bb0f48aeec5cd312f2535c8891a7ad727b202d7278ad590ade86eeb",
            "X-Key-Username" to "0742ae2df5029929b81c9ce3b09151",
            "X-Package-Name" to "com.expertapp.movielixmedia",
            "X-Version-Code" to "84",
            "X-Version-Name" to "1.8.4"
        )
    }

    private fun getResponseBodyString(response: Response): String {
        val rawBytes = response.body?.bytes() ?: return "{}"
        if (rawBytes.isEmpty()) return "{}"

        val isGzip = rawBytes.size >= 2 && (rawBytes[0] == 0x1f.toByte()) && (rawBytes[1] == 0x8b.toByte())
        return try {
            if (isGzip || "gzip".equals(response.header("Content-Encoding"), ignoreCase = true)) {
                val gis = GZIPInputStream(ByteArrayInputStream(rawBytes))
                val reader = InputStreamReader(gis, Charsets.UTF_8)
                reader.readText()
            } else {
                String(rawBytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            String(rawBytes, Charsets.UTF_8)
        }
    }

    private fun makeDirectRequest(endpoint: String, data: Map<String, String>, useToken: Boolean): JSONObject? {
        val fullUrl = baseUrl + endpoint
        val formBuilder = FormBody.Builder()
        for ((k, v) in data) {
            formBuilder.add(k, v)
        }
        if (useToken && !token.isNullOrEmpty()) {
            formBuilder.add("token", token!!)
        }

        val reqBuilder = Request.Builder().url(fullUrl).post(formBuilder.build())
        for ((hk, hv) in getHeaders()) {
            reqBuilder.header(hk, hv)
        }

        return try {
            val response = client.newCall(reqBuilder.build()).execute()
            val text = getResponseBodyString(response)
            JSONObject(text)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Direct request error for $endpoint: ${e.message}")
            null
        }
    }

    private fun makeRequest(endpoint: String, data: MutableMap<String, String>, retryCount: Int = 0): JSONObject {
        // Download/streaming links must ALWAYS be fetched fresh — never cache link-info-request
        val isCacheable = endpoint.contains("detail-info") ||
                endpoint.contains("episode-request")

        val sortedData = data.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val cacheKey = "movielix_${endpoint.trim('/')}_$sortedData"

        if (isCacheable && retryCount == 0) {
            val cachedJsonStr = AppCacheManager.get(cacheKey)
            if (!cachedJsonStr.isNullOrEmpty()) {
                try {
                    val cachedObj = JSONObject(cachedJsonStr)
                    AppLogger.s(TAG, "⚡ پاسخ سریع از کش هوشمند: $endpoint ($sortedData)")
                    return cachedObj
                } catch (_: Exception) {}
            }
        }

        if (token.isNullOrEmpty()) {
            loadOrRefreshToken()
        }

        val fullUrl = baseUrl + endpoint
        val formBuilder = FormBody.Builder()
        for ((k, v) in data) {
            formBuilder.add(k, v)
        }
        if (!token.isNullOrEmpty()) {
            formBuilder.add("token", token!!)
        }

        val reqBuilder = Request.Builder().url(fullUrl).post(formBuilder.build())
        for ((hk, hv) in getHeaders()) {
            reqBuilder.header(hk, hv)
        }

        AppLogger.d(TAG, "-> POST $endpoint (پارامترها: ${data.keys.joinToString()})")

        return try {
            val response = client.newCall(reqBuilder.build()).execute()
            val bodyString = getResponseBodyString(response)
            val result = JSONObject(bodyString)

            val msg = result.optString("message", "")
            if (msg.contains("اجازه استفاده") || msg.contains("توکن ارسالی اشتباه") || msg.contains("token")) {
                AppLogger.w(TAG, "هشدار توکن نامعتبر در پاسخ ($msg). تمدید مجدد جلسه...")
                if (retryCount < 2) {
                    tokenFile.delete()
                    refreshToken()
                    Thread.sleep(1000)
                    return makeRequest(endpoint, data, retryCount + 1)
                }
            }

            if (!response.isSuccessful && retryCount < 2) {
                AppLogger.w(TAG, "کد وضعیت HTTP ناموفق (${response.code}). تلاش مجدد...")
                Thread.sleep(1000)
                return makeRequest(endpoint, data, retryCount + 1)
            }
            AppLogger.s(TAG, "<- پاسخ دریافت شد از $endpoint [HTTP ${response.code}]")

            if (isCacheable && response.isSuccessful) {
                AppCacheManager.put(cacheKey, bodyString)
            }

            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "خطای اتصال به $endpoint: ${e.message}")
            if (retryCount < 2) {
                Thread.sleep(1000)
                return makeRequest(endpoint, data, retryCount + 1)
            }
            val errObj = JSONObject()
            errObj.put("error", e.message ?: "Network error")
            errObj
        }
    }

    suspend fun getVitrinSections(page: Int = 1): Pair<List<MovieItem>, List<VitrinSection>> = withContext(Dispatchers.IO) {
        val res = makeRequest("/home/main-page", mutableMapOf(
            "type" to "0",
            "action" to "0",
            "genre_id" to "-1",
            "page" to page.toString()
        ))

        val banners = mutableListOf<MovieItem>()
        val sections = mutableListOf<VitrinSection>()

        if (page == 1) {
            val sliderArr = res.optJSONArray("slider")
            if (sliderArr != null) {
                for (i in 0 until sliderArr.length()) {
                    val s = sliderArr.getJSONObject(i)
                    banners.add(MovieItem(
                        id = s.optInt("id"),
                        title = s.optString("name", s.optString("title")),
                        image = s.optString("image"),
                        type = s.optInt("type")
                    ))
                }
            }
        }

        val catObj = res.optJSONObject("category")
        val dataArr = catObj?.optJSONArray("data")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val cat = dataArr.getJSONObject(i)
                val catId = cat.optInt("id")
                val catTitle = cat.optString("title")
                val itemsList = mutableListOf<MovieItem>()

                val details = cat.optJSONArray("detail")
                if (details != null) {
                    for (j in 0 until details.length()) {
                        val it = details.getJSONObject(j)
                        itemsList.add(MovieItem(
                            id = it.optInt("id"),
                            title = it.optString("title", it.optString("name")),
                            image = it.optString("image"),
                            type = it.optInt("type"),
                            year = if (it.has("year")) it.optString("year") else null,
                            hasSub = it.optInt("is_sub", 0) == 1,
                            hasDub = it.optInt("is_dubbed", 0) == 1
                        ))
                    }
                }
                sections.add(VitrinSection(catId, catTitle, itemsList))
            }
        }
        AppLogger.i(TAG, "صفحه $page ویترین: ${sections.size} دسته‌بندی دریافت شد")
        Pair(banners, sections)
    }

    suspend fun search(q: String, type: Int = 2, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val res = makeRequest("/movie/search", mutableMapOf(
            "type" to type.toString(),
            "page" to page.toString(),
            "value" to q,
            "priority" to "6",
            "language_id" to "0"
        ))
        val items = mutableListOf<MovieItem>()
        val infoObj = res.optJSONObject("info")
        val dataArr = infoObj?.optJSONArray("data") ?: res.optJSONArray("data")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val it = dataArr.getJSONObject(i)
                items.add(MovieItem(
                    id = it.optInt("id"),
                    title = it.optString("title", it.optString("name")),
                    image = it.optString("image"),
                    type = it.optInt("type"),
                    year = if (it.has("year")) it.optString("year") else null,
                    hasSub = it.optInt("is_sub", 0) == 1,
                    hasDub = it.optInt("is_dubbed", 0) == 1
                ))
            }
        }
        AppLogger.i(TAG, "جستجو '$q' (صفحه $page): ${items.size} نتیجه")
        items
    }

    suspend fun getMovieDetails(id: Int): MovieDetail? = withContext(Dispatchers.IO) {
        val res = makeRequest("/movie/detail-info", mutableMapOf("id" to id.toString()))
        val info = res.optJSONObject("info") ?: return@withContext null

        val seasonsList = mutableListOf<SeasonItem>()
        val linkArr = res.optJSONArray("link")
        if (linkArr != null) {
            for (i in 0 until linkArr.length()) {
                val s = linkArr.getJSONObject(i)
                val sNum = s.optInt("season", i + 1)
                val sTitle = s.optString("title", "فصل $sNum")
                seasonsList.add(SeasonItem(sNum, sTitle))
            }
        }

        MovieDetail(
            id = id,
            title = info.optString("title", info.optString("name")),
            image = info.optString("image"),
            banner = info.optString("banner", info.optString("image")),
            type = info.optInt("type", 0),
            imdbRate = if (info.has("imdb_rate")) info.optString("imdb_rate") else null,
            duration = if (info.has("duration")) info.optString("duration") else null,
            year = if (info.has("year")) info.optString("year") else null,
            description = if (info.has("description")) info.optString("description") else null,
            descriptionAi = if (info.has("description_ai")) info.optString("description_ai") else null,
            seasons = seasonsList
        )
    }

    suspend fun getEpisodes(id: Int, season: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val res = makeRequest("/movie/episode-request", mutableMapOf(
            "id" to id.toString(),
            "season" to season.toString(),
            "reverse" to "0",
            "page" to "1"
        ))
        val list = mutableListOf<EpisodeItem>()
        val dataArr = res.optJSONArray("data")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val ep = dataArr.getJSONObject(i)
                val epNum = ep.optInt("episode", i + 1)
                val epTitle = ep.optString("title", "قسمت $epNum")

                val qList = mutableListOf<QualityItem>()
                val qs = ep.optJSONArray("quality")
                if (qs != null) {
                    for (j in 0 until qs.length()) {
                        val q = qs.getJSONObject(j)
                        qList.add(QualityItem(
                            id = q.optInt("id"),
                            type = q.optString("quality_type"),
                            title = q.optString("quality_title"),
                            size = q.optString("size")
                        ))
                    }
                }

                list.add(EpisodeItem(
                    episode = epNum,
                    title = epTitle,
                    qualities = qList
                ))
            }
        }
        AppLogger.i(TAG, "سریال $id فصل $season: ${list.size} قسمت دریافت شد")
        list
    }

    suspend fun getQualities(id: Int, season: Int = -1, episode: Int = -1): List<QualityItem> = withContext(Dispatchers.IO) {
        val details = makeRequest("/movie/detail-info", mutableMapOf("id" to id.toString()))
        val isSeries = details.optJSONObject("info")?.optInt("type", 0) == 1
        val qualitiesArr = mutableListOf<QualityItem>()

        if (isSeries && season != -1 && episode != -1) {
            val epData = makeRequest("/movie/episode-request", mutableMapOf(
                "id" to id.toString(),
                "season" to season.toString(),
                "reverse" to "0",
                "page" to "1"
            ))
            val episodes = epData.optJSONArray("data")
            if (episodes != null) {
                for (i in 0 until episodes.length()) {
                    val ep = episodes.getJSONObject(i)
                    if (ep.optInt("episode") == episode) {
                        val qs = ep.optJSONArray("quality") ?: JSONArray()
                        for (j in 0 until qs.length()) {
                            val q = qs.getJSONObject(j)
                            qualitiesArr.add(QualityItem(
                                id = q.optInt("id"),
                                type = q.optString("quality_type"),
                                title = q.optString("quality_title"),
                                size = q.optString("size")
                            ))
                        }
                        break
                    }
                }
            }
        } else {
            val link = details.optJSONArray("link")
            val ep = link?.optJSONObject(0)?.optJSONArray("episode")?.optJSONObject(0)
            val qs = ep?.optJSONArray("quality")
            if (qs != null) {
                for (j in 0 until qs.length()) {
                    val q = qs.getJSONObject(j)
                    qualitiesArr.add(QualityItem(
                        id = q.optInt("id"),
                        type = q.optString("quality_type"),
                        title = q.optString("quality_title"),
                        size = q.optString("size")
                    ))
                }
            }
        }
        qualitiesArr
    }

    suspend fun getStreamUrl(id: Int, qualityId: Int, season: Int = -1, episode: Int = -1): String? = withContext(Dispatchers.IO) {
        val details = makeRequest("/movie/detail-info", mutableMapOf("id" to id.toString()))
        val isSeries = details.optJSONObject("info")?.optInt("type", 0) == 1
        var movieLinkId: Int? = null
        var movieQualityId: Int? = null
        var isClone = 1

        if (isSeries && season != -1 && episode != -1) {
            val epData = makeRequest("/movie/episode-request", mutableMapOf(
                "id" to id.toString(),
                "season" to season.toString(),
                "reverse" to "0",
                "page" to "1"
            ))
            val episodes = epData.optJSONArray("data")
            if (episodes != null) {
                for (i in 0 until episodes.length()) {
                    val ep = episodes.getJSONObject(i)
                    if (ep.optInt("episode") == episode) {
                        val qs = ep.optJSONArray("quality") ?: JSONArray()
                        for (j in 0 until qs.length()) {
                            val q = qs.getJSONObject(j)
                            if (q.optInt("id") == qualityId) {
                                movieLinkId = q.optInt("id")
                                movieQualityId = q.optInt("movie_quality_id", 2)
                                isClone = q.optInt("download_clone", 1)
                                break
                            }
                        }
                    }
                }
            }
        } else {
            val link = details.optJSONArray("link")
            val ep = link?.optJSONObject(0)?.optJSONArray("episode")?.optJSONObject(0)
            val qs = ep?.optJSONArray("quality")
            if (qs != null) {
                for (j in 0 until qs.length()) {
                    val q = qs.getJSONObject(j)
                    if (q.optInt("id") == qualityId) {
                        movieLinkId = q.optInt("id")
                        movieQualityId = q.optInt("movie_quality_id", 2)
                        isClone = q.optInt("download_clone", 1)
                        break
                    }
                }
            }
        }

        if (movieLinkId == null) return@withContext null

        val linkInfo = makeRequest("/movie/link-info-request", mutableMapOf(
            "movie_id" to id.toString(),
            "movie_link_id" to movieLinkId.toString(),
            "movie_quality_id" to (movieQualityId ?: 2).toString(),
            "is_clone" to "1",
            "type" to if (isSeries) "1" else "0"
        ))

        val l = linkInfo.optJSONObject("link")
        val cloneUrl = l?.optString("url_clone")?.takeIf { it.isNotBlank() }
        val stdUrl = l?.optString("url")?.takeIf { it.isNotBlank() }
        var finalUrl = cloneUrl ?: stdUrl

        if (finalUrl != null && finalUrl.contains("expertappmedia.org")) {
            finalUrl = finalUrl.replace(Regex("pro([0-9]+)\\.expertappmedia\\.org"), "pro$1sub.expertapp.org")
        }
        AppLogger.s(TAG, "لینک پخش/دانلود استخراج شد: $finalUrl")
        finalUrl
    }
}
