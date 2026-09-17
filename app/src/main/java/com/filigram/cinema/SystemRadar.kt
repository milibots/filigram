package com.filigram.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ServiceHealth(
    val id: String,
    val name: String,
    val description: String,
    val latencyMs: Long,
    val isOperational: Boolean,
    val isCore: Boolean
)

object SystemRadar {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkAllEngines(): List<ServiceHealth> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ServiceHealth>()

        list.add(pingService(
            id = "movielix",
            name = "موتور اصلی موویلیکس (Movielix Core)",
            description = "سرور اختصاصی استریم و دانلود مستقیم (پیش‌فرض)",
            url = "https://global-api2.expertmedias.org/apiMovielix.php",
            isCore = true
        ))

        list.add(pingService(
            id = "rezflix",
            name = "موتور رزفلیکس (RezFlix Engine)",
            description = "آرشیو فیلم‌ها و سریال‌های دوبله و زیرنویس (بتا)",
            url = "http://server-win-iran.info/api/movie/by/filtres/0/created/0/4F5A9C3D9A86FA54EACEDDD635185/?page=1",
            isCore = false
        ))

        list.add(pingService(
            id = "filmjoo",
            name = "موتور فیلم‌جو / دلفان (FilmJoo Engine)",
            description = "سرور استخراج محتوای سینمایی دلفان پلاس (بتا)",
            url = "http://tahlilgaraan.ir/app-plus/vp1.php?key=pwep5d4sdoe0ewsosa7d563d&action=filter_search&pageno=1",
            isCore = false
        ))

        list.add(pingService(
            id = "almasmovie",
            name = "موتور الماس‌مووی (AlmasMovie Engine)",
            description = "سرویس مستقیم و پایگاه دانلود و استریم اختصاصی اندروید",
            url = "https://almasandroid.com/api/almas/v1/config/",
            isCore = false
        ))

        list.add(pingService(
            id = "nextmovie",
            name = "موتور نکست‌مووی (NextMovie Engine)",
            description = "سرور استریم و دانلود مستقیم فیلم و سریال",
            url = "https://mihan-cdn.com",
            isCore = false
        ))

        list.add(pingService(
            id = "bj",
            name = "موتور هوشمند BJ (پایگاه MAPI)",
            description = "پایگاه داده فیلم و سریال با لینک مستقیم و استریم آنلاین",
            url = "https://forooshonline20.ir/wp-json/mapi/v1/post/movies?page=1&per_page=1",
            isCore = false
        ))

        list
    }

    private fun pingService(
        id: String,
        name: String,
        description: String,
        url: String,
        isCore: Boolean
    ): ServiceHealth {
        val t0 = System.currentTimeMillis()
        var ok = false
        var latency = 0L

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val resp = client.newCall(req).execute()
            latency = System.currentTimeMillis() - t0
            ok = resp.isSuccessful || resp.code in 200..399
            resp.close()
        } catch (e: Exception) {
            latency = System.currentTimeMillis() - t0
            ok = false
        }

        return ServiceHealth(
            id = id,
            name = name,
            description = description,
            latencyMs = latency,
            isOperational = ok,
            isCore = isCore
        )
    }
}
