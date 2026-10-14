package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Read-only adapter for the Simba / Fitlinerim Android API. */
object SimbaApi {
    private const val TAG = "SimbaApi"
    private const val KEY = "simba"
    private const val BASE_URL = "http://fitlinerim.info"
    private const val STATIC_KEY = "4F5A9C3D9A86FA54EACEDDD635185"
    private val FALLBACK_HEADERS = mapOf(
        "User-Agent" to "okhttp/4.9.3",
        "Accept" to "application/json, text/html, video/*, */*",
        "Accept-Encoding" to "gzip, deflate",
        "Connection" to "keep-alive"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val itemCache = ConcurrentHashMap<Int, JSONObject>()
    private val seasonCache = ConcurrentHashMap<Int, List<JSONObject>>()

    private fun base() = SourceConfig.baseUrl(KEY, BASE_URL).trimEnd('/')
    private fun path(value: String) = "$value/$STATIC_KEY/"

    private fun request(path: String): Any? = try {
        val builder = Request.Builder().url("${base()}$path")
        SourceConfig.headers(KEY, FALLBACK_HEADERS).forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val isGzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            val text = if (isGzip) {
                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "خطا در درخواست $path: ${e.message}")
        null
    }

    private fun array(payload: Any?, vararg keys: String): JSONArray {
        if (payload is JSONArray) return payload
        if (payload is JSONObject) {
            val code = payload.opt("code")?.toString()
            if (code != null && code !in setOf("0", "1", "200")) return JSONArray()
            for (key in keys) payload.optJSONArray(key)?.let { return it }
            val values = payload.optJSONArray("values")
            if (values != null) return values
        }
        return JSONArray()
    }

    private fun absolute(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching { URI(base() + "/").resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun movie(raw: JSONObject, forcedType: Int? = null): MovieItem {
        val id = raw.optInt("id")
        itemCache[id] = raw
        val type = forcedType ?: if (raw.optBoolean("is_serial") || raw.optString("type") == "serie") 1 else 0
        return MovieItem(
            id = id,
            title = raw.optString("title", "بدون عنوان"),
            image = absolute(raw.optString("image")),
            type = type,
            year = raw.opt("year")?.toString()?.takeIf { it != "null" },
            rating = raw.opt("imdb")?.toString()?.takeIf { it != "null" },
            engine = KEY
        )
    }

    private suspend fun list(endpoint: String, type: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        val data = array(request(path(endpoint)), if (type == 1) "series" else "movies", "data")
        (0 until data.length()).mapNotNull { data.optJSONObject(it)?.let { obj -> movie(obj, type) } }
    }

    suspend fun getMovies(page: Int = 1) = list("/api/movie/by/filtres/0/created/0", 0)
    suspend fun getSeries(page: Int = 1) = list("/api/serie/by/filtres/0/created/0", 1)
    suspend fun getRecent(page: Int = 1): List<MovieItem> = (getMovies(page) + getSeries(page)).distinctBy { it.id to it.type }

    suspend fun search(query: String, page: Int = 1): List<MovieItem> {
        if (query.isBlank()) return getRecent(page)
        return getRecent(page).filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    suspend fun getHomeSections(): List<VitrinSection> = withContext(Dispatchers.IO) {
        val movies = getMovies(1)
        val series = getSeries(1)
        buildList {
            if (movies.isNotEmpty()) add(VitrinSection(1, "فیلم‌های سیاره مریخ", movies))
            if (series.isNotEmpty()) add(VitrinSection(2, "سریال‌های سیاره مریخ", series))
        }
    }

    private fun loadSeasons(seriesId: Int): List<JSONObject> {
        seasonCache[seriesId]?.let { return it }
        val data = array(request(path("/api/season/by/serie/$seriesId")), "seasons", "data")
        val result = (0 until data.length()).mapNotNull { data.optJSONObject(it) }
        seasonCache[seriesId] = result
        return result
    }

    private fun qualities(raw: JSONObject): List<QualityItem> {
        val sources = raw.optJSONArray("sources") ?: return emptyList()
        return (0 until sources.length()).mapNotNull { index ->
            val source = sources.optJSONObject(index) ?: return@mapNotNull null
            val url = absolute(source.optString("url"))
            if (url.isBlank()) return@mapNotNull null
            val label = source.opt("quality")?.toString()?.takeIf { it.isNotBlank() } ?: "کیفیت اصلی"
            QualityItem(
                id = source.optInt("id", index + 1),
                type = source.optString("type", if (url.contains(".m3u8", true)) "HLS" else "MP4").uppercase(),
                title = if (label.all { it.isDigit() }) "${label}p" else label,
                size = source.optString("size", "مستقیم"),
                directUrl = url
            )
        }
    }

    suspend fun getDetails(id: Int): MovieDetail? = withContext(Dispatchers.IO) {
        var raw = itemCache[id]
        if (raw == null) {
            getRecent(1)
            raw = itemCache[id]
        }
        raw ?: return@withContext null
        val isSeries = raw.optBoolean("is_serial") || raw.optString("type") == "serie"
        val seasons = if (isSeries) loadSeasons(id).mapIndexed { index, season ->
            SeasonItem(season.optInt("id", index + 1), season.optString("title", "فصل ${index + 1}"))
        } else emptyList()
        MovieDetail(
            id = id,
            title = raw.optString("title", "بدون عنوان"),
            image = absolute(raw.optString("image")),
            banner = absolute(raw.optString("cover", raw.optString("image"))),
            type = if (isSeries) 1 else 0,
            imdbRate = raw.opt("imdb")?.toString(),
            duration = null,
            year = raw.opt("year")?.toString(),
            description = raw.optString("description"),
            descriptionAi = null,
            seasons = seasons,
            directQualities = if (isSeries) emptyList() else qualities(raw)
        )
    }

    suspend fun getEpisodes(seriesId: Int, seasonNumber: Int): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val seasons = loadSeasons(seriesId)
        val season = seasons.firstOrNull { it.optInt("id") == seasonNumber }
            ?: seasons.getOrNull(seasonNumber - 1) ?: return@withContext emptyList()
        val episodes = season.optJSONArray("episodes") ?: return@withContext emptyList()
        (0 until episodes.length()).mapNotNull { index ->
            val ep = episodes.optJSONObject(index) ?: return@mapNotNull null
            EpisodeItem(ep.optInt("id", index + 1), ep.optString("title", "قسمت ${index + 1}"), qualities(ep))
        }
    }

    suspend fun getQualities(movieId: Int): List<QualityItem> = withContext(Dispatchers.IO) {
        var raw = itemCache[movieId]
        if (raw == null) {
            getMovies(1)
            raw = itemCache[movieId]
        }
        raw?.let(::qualities) ?: emptyList()
    }
}
