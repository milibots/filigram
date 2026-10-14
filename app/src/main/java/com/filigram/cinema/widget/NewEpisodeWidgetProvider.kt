package com.filigram.cinema.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import com.filigram.cinema.MainActivity
import com.filigram.cinema.R
import com.filigram.cinema.SeriesNotificationHelper
import com.filigram.cinema.SeriesSubscription
import com.filigram.cinema.SeriesSubscriptionManager
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Home-screen widget: cover art of the latest new series episode with an "اپیزود جدید" badge. */
class NewEpisodeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    companion object {
        private const val PREFS = "filigram_new_episode_widget"
        private const val KEY_EVENT = "event"
        private const val COVER_FILE = "new_episode_cover.jpg"
        private const val MAX_COVER_PX = 480

        /** Called from the background worker when a new episode is found. Must run off the main thread. */
        fun record(context: Context, series: SeriesSubscription, season: Int, episode: Int, episodeTitle: String?) {
            val event = JSONObject().apply {
                put("id", series.id)
                put("title", series.title)
                put("image", series.image)
                put("engine", series.engine)
                put("slug", series.slug ?: "")
                put("season", season)
                put("episode", episode)
                put("episodeTitle", episodeTitle ?: "")
                put("ts", System.currentTimeMillis())
            }
            val cover = downloadCover(series.image)
            val file = File(context.filesDir, COVER_FILE)
            if (cover != null) {
                file.outputStream().use { cover.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            } else {
                file.delete()
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_EVENT, event.toString()).apply()
            refreshAll(context)
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NewEpisodeWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun downloadCover(url: String): Bitmap? {
            if (url.isBlank()) return null
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                val raw = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
                val scale = MAX_COVER_PX.toFloat() / maxOf(raw.width, raw.height)
                if (scale >= 1f) raw
                else Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
            } catch (_: Exception) {
                null
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_new_episode)
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENT, null)
            val event = raw?.let { runCatching { JSONObject(it) }.getOrNull() }

            if (event == null) {
                SeriesSubscriptionManager.init(context)
                val fallback = SeriesSubscriptionManager.getAllActiveSubscriptions(context)
                    .filter { it.lastKnownEpisode > 0 }
                    .maxByOrNull { it.subscribedAt }
                if (fallback == null) {
                    views.setTextViewText(R.id.newEpTitle, "سریالی را دنبال نکرده‌اید")
                    views.setTextViewText(R.id.newEpSubtitle, "در صفحه سریال، زنگ اعلان را بزنید")
                    views.setViewVisibility(R.id.newEpBadge, View.GONE)
                    views.setImageViewResource(R.id.newEpCover, R.mipmap.ic_launcher)
                    views.setOnClickPendingIntent(R.id.newEpRoot, openApp(context))
                } else {
                    bind(
                        context, views, fallback.id, fallback.title, fallback.image, fallback.engine,
                        fallback.slug.orEmpty(), fallback.lastKnownSeason, fallback.lastKnownEpisode, false
                    )
                }
            } else {
                bind(
                    context, views, event.optInt("id"), event.optString("title"), event.optString("image"),
                    event.optString("engine"), event.optString("slug"), event.optInt("season"),
                    event.optInt("episode"), true
                )
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun bind(
            context: Context, views: RemoteViews, id: Int, title: String, image: String,
            engine: String, slug: String, season: Int, episode: Int, isNew: Boolean
        ) {
            views.setTextViewText(R.id.newEpTitle, title)
            views.setTextViewText(R.id.newEpSubtitle, "فصل $season  •  قسمت $episode")
            views.setTextViewText(R.id.newEpBadge, if (isNew) "اپیزود جدید" else "آخرین قسمت")
            views.setViewVisibility(R.id.newEpBadge, View.VISIBLE)

            val file = File(context.filesDir, COVER_FILE)
            val bmp = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            if (bmp != null) views.setImageViewBitmap(R.id.newEpCover, bmp)
            else views.setImageViewResource(R.id.newEpCover, R.mipmap.ic_launcher)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = SeriesNotificationHelper.ACTION_OPEN_SERIES
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_ID, id)
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_TITLE, title)
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_IMAGE, image)
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_ENGINE, engine)
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_SLUG, slug.ifBlank { null })
                putExtra(SeriesNotificationHelper.EXTRA_OPEN_SERIES_TYPE, 1)
            }
            views.setOnClickPendingIntent(
                R.id.newEpRoot,
                PendingIntent.getActivity(
                    context, 20, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 21, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
