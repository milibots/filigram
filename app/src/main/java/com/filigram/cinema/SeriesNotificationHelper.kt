package com.filigram.cinema

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object SeriesNotificationHelper {

    const val CHANNEL_ID = "channel_filigram_series"
    private const val CHANNEL_NAME = "اعلان قسمت‌های جدید سریال‌ها"
    private const val CHANNEL_DESC = "اطلاع‌رسانی فوری به محض انتشار قسمت یا فصل جدید سریال‌های نشان‌شده شما"

    const val ACTION_OPEN_SERIES = "com.filigram.cinema.ACTION_OPEN_SERIES"
    const val EXTRA_OPEN_SERIES_ID = "extra_open_series_id"
    const val EXTRA_OPEN_SERIES_TITLE = "extra_open_series_title"
    const val EXTRA_OPEN_SERIES_IMAGE = "extra_open_series_image"
    const val EXTRA_OPEN_SERIES_ENGINE = "extra_open_series_engine"
    const val EXTRA_OPEN_SERIES_SLUG = "extra_open_series_slug"
    const val EXTRA_OPEN_SERIES_TYPE = "extra_open_series_type"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.parseColor("#FFD700")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun sendNewEpisodeNotification(
        context: Context,
        series: SeriesSubscription,
        season: Int,
        episode: Int,
        episodeTitle: String? = null
    ) {
        createNotificationChannel(context)

        // Intent to open MainActivity and deep-link directly to series details
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SERIES
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SERIES_ID, series.id)
            putExtra(EXTRA_OPEN_SERIES_TITLE, series.title)
            putExtra(EXTRA_OPEN_SERIES_IMAGE, series.image)
            putExtra(EXTRA_OPEN_SERIES_ENGINE, series.engine)
            putExtra(EXTRA_OPEN_SERIES_SLUG, series.slug)
            putExtra(EXTRA_OPEN_SERIES_TYPE, 1) // 1 = series
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            series.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "قسمت جدید منتشر شد: ${series.title}"
        val content = if (!episodeTitle.isNullOrEmpty()) {
            "فصل $season - قسمت $episode: $episodeTitle 🎬"
        } else {
            "فصل $season - قسمت $episode در دسترس قرار گرفت 🎬"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell_gold)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setColor(Color.parseColor("#D4AF37")) // Filigram Gold
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(series.id, builder.build())
            AppLogger.i("SeriesNotificationHelper", "اعلان انتشار قسمت جدید برای «${series.title}» ارسال شد.")
        } catch (e: SecurityException) {
            AppLogger.e("SeriesNotificationHelper", "عدم دسترسی به ارسال اعلان: ${e.message}")
        } catch (e: Exception) {
            AppLogger.e("SeriesNotificationHelper", "خطا در ارسال اعلان: ${e.message}")
        }
    }
}
