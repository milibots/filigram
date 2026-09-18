package com.filigram.cinema.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.filigram.cinema.AlmasMovieApi
import com.filigram.cinema.AnnouncementsManager
import com.filigram.cinema.AppLogger
import com.filigram.cinema.BjApi
import com.filigram.cinema.MovieItem
import com.filigram.cinema.MovielixApi
import com.filigram.cinema.NextMovieApi
import com.filigram.cinema.R
import com.filigram.cinema.RezFlixApi
import com.filigram.cinema.SourceConfig
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL

class FiligramWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val mode = intent.getStringExtra(FiligramWidgetProvider.EXTRA_MODE) ?: FiligramWidgetProvider.MODE_MOVIES
        return FiligramWidgetFactory(applicationContext, mode)
    }
}

private data class WidgetRow(
    val id: Int,
    val title: String,
    val subtitle: String,
    val image: String,
    val type: Int,
    val engine: String,
    val isAnnouncement: Boolean
)

private class FiligramWidgetFactory(
    private val context: Context,
    private val mode: String
) : RemoteViewsService.RemoteViewsFactory {

    private val rows = mutableListOf<WidgetRow>()
    private val posters = mutableMapOf<String, Bitmap>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows.clear()
        posters.clear()
        try {
            if (mode == FiligramWidgetProvider.MODE_NEWS) {
                loadAnnouncements()
            } else {
                loadTitles()
            }
        } catch (e: Exception) {
            AppLogger.e("FiligramWidget", "خطا در بارگذاری ویجت: ${e.message}")
        }
    }

    private fun activeEngine(): String {
        val prefs = context.getSharedPreferences("filigram_prefs", Context.MODE_PRIVATE)
        return prefs.getString("active_engine", "movielix") ?: "movielix"
    }

    private fun loadAnnouncements() = runBlocking {
        val list = AnnouncementsManager.fetchAnnouncements(context)
        list.take(MAX_ITEMS).forEach { item ->
            rows.add(
                WidgetRow(
                    id = item.id.hashCode(),
                    title = item.title,
                    subtitle = item.text.replace("\n", " ").take(90),
                    image = "",
                    type = 0,
                    engine = "",
                    isAnnouncement = true
                )
            )
        }
    }

    private fun loadTitles() = runBlocking {
        // The widget runs without MainActivity, so it loads the source config itself.
        SourceConfig.load(context)
        val engine = activeEngine()
        val wantSeries = mode == FiligramWidgetProvider.MODE_SERIES

        val items: List<MovieItem> = when (engine) {
            "almasmovie" -> AlmasMovieApi.getRecent(1).filter { it.type == if (wantSeries) 1 else 0 }
            "nextmovie" -> NextMovieApi.getRecent(1).filter { it.type == if (wantSeries) 1 else 0 }
            "rezflix" -> RezFlixApi.getMovies(1).filter { it.type == if (wantSeries) 1 else 0 }
            "bj" -> if (wantSeries) BjApi.getSeries(1) else BjApi.getMovies(1)
            else -> MovielixApi(context).search("", type = if (wantSeries) 1 else 0, page = 1)
        }

        items.take(MAX_ITEMS).forEach { item ->
            rows.add(
                WidgetRow(
                    id = item.id,
                    title = item.title,
                    subtitle = listOfNotNull(
                        item.year,
                        if (item.hasDub) "دوبله" else if (item.hasSub) "زیرنویس" else null
                    ).joinToString(" • "),
                    image = item.image,
                    type = item.type,
                    engine = engine,
                    isAnnouncement = false
                )
            )
        }

        rows.forEach { row ->
            if (row.image.isNotBlank()) {
                downloadPoster(row.image)?.let { posters[row.image] = it }
            }
        }
    }

    private fun downloadPoster(url: String): Bitmap? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            }
            connection.inputStream.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        rows.clear()
        posters.clear()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        views.setTextViewText(R.id.widgetItemTitle, row.title)
        views.setTextViewText(R.id.widgetItemSubtitle, row.subtitle)

        val poster = posters[row.image]
        if (poster != null) {
            views.setImageViewBitmap(R.id.widgetItemPoster, poster)
        } else {
            views.setImageViewResource(R.id.widgetItemPoster, R.mipmap.ic_launcher)
        }

        val fillIntent = Intent().apply {
            putExtra(FiligramWidgetProvider.EXTRA_ITEM_ID, row.id)
            putExtra(FiligramWidgetProvider.EXTRA_ITEM_TITLE, row.title)
            putExtra(FiligramWidgetProvider.EXTRA_ITEM_IMAGE, row.image)
            putExtra(FiligramWidgetProvider.EXTRA_ITEM_TYPE, row.type)
            putExtra(FiligramWidgetProvider.EXTRA_ITEM_ENGINE, row.engine)
            putExtra(FiligramWidgetProvider.EXTRA_MODE, mode)
        }
        views.setOnClickFillInIntent(R.id.widgetItemRoot, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = rows[position].id.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val MAX_ITEMS = 15
    }
}
