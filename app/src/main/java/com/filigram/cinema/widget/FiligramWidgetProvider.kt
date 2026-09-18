package com.filigram.cinema.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.filigram.cinema.MainActivity
import com.filigram.cinema.R

class FiligramWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> renderWidget(context, appWidgetManager, widgetId) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, FiligramWidgetProvider::class.java))
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
                ids.forEach { renderWidget(context, manager, it) }
            }
            ACTION_SET_MODE -> {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MOVIES
                WidgetPreferences.setMode(context, mode)
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, FiligramWidgetProvider::class.java))
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
                ids.forEach { renderWidget(context, manager, it) }
            }
        }
    }

    private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_filigram)
        val mode = WidgetPreferences.getMode(context)

        views.setTextViewText(R.id.widgetSubtitle, WidgetPreferences.modeLabel(mode))

        val serviceIntent = Intent(context, FiligramWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_MODE, mode)
            // A unique data uri keeps RemoteViewsService from reusing a factory of the previous mode.
            data = Uri.parse("filigram://widget/$widgetId/$mode")
        }
        views.setRemoteAdapter(R.id.widgetList, serviceIntent)
        views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

        views.setOnClickPendingIntent(R.id.widgetHeader, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widgetRefresh, broadcast(context, ACTION_REFRESH, null, 1))
        views.setOnClickPendingIntent(R.id.widgetTabMovies, broadcast(context, ACTION_SET_MODE, MODE_MOVIES, 2))
        views.setOnClickPendingIntent(R.id.widgetTabSeries, broadcast(context, ACTION_SET_MODE, MODE_SERIES, 3))
        views.setOnClickPendingIntent(R.id.widgetTabNews, broadcast(context, ACTION_SET_MODE, MODE_NEWS, 4))

        val itemClickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ITEM
        }
        views.setPendingIntentTemplate(
            R.id.widgetList,
            PendingIntent.getActivity(
                context,
                5,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        manager.updateAppWidget(widgetId, views)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcast(context: Context, action: String, mode: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, FiligramWidgetProvider::class.java).apply {
            this.action = action
            if (mode != null) putExtra(EXTRA_MODE, mode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_REFRESH = "com.filigram.cinema.widget.ACTION_REFRESH"
        const val ACTION_SET_MODE = "com.filigram.cinema.widget.ACTION_SET_MODE"
        const val ACTION_OPEN_ITEM = "com.filigram.cinema.widget.ACTION_OPEN_ITEM"

        const val EXTRA_MODE = "widget_mode"
        const val EXTRA_ITEM_ID = "widget_item_id"
        const val EXTRA_ITEM_TITLE = "widget_item_title"
        const val EXTRA_ITEM_IMAGE = "widget_item_image"
        const val EXTRA_ITEM_TYPE = "widget_item_type"
        const val EXTRA_ITEM_ENGINE = "widget_item_engine"

        const val MODE_MOVIES = "movies"
        const val MODE_SERIES = "series"
        const val MODE_NEWS = "news"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FiligramWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }
    }
}

object WidgetPreferences {
    private const val PREFS = "filigram_widget_prefs"
    private const val KEY_MODE = "widget_mode"

    fun getMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, FiligramWidgetProvider.MODE_MOVIES) ?: FiligramWidgetProvider.MODE_MOVIES

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply()
    }

    fun modeLabel(mode: String): String = when (mode) {
        FiligramWidgetProvider.MODE_SERIES -> "سریال‌های تازه"
        FiligramWidgetProvider.MODE_NEWS -> "اعلان‌های فیلیگرام"
        else -> "جدیدترین فیلم‌ها"
    }
}
