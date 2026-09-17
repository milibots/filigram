package com.filigram.cinema

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SeriesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            checkAndNotifyUpdates(applicationContext)
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("SeriesUpdateWorker", "خطا در بررسی قسمت‌های جدید سریال‌ها: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "FiligramSeriesUpdateWorker"

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SeriesUpdateWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            AppLogger.i("SeriesUpdateWorker", "بررسی دوره‌ای قسمت‌های جدید هر ۲ ساعت زمان‌بندی شد.")
        }

        suspend fun checkAndNotifyUpdates(context: Context) = withContext(Dispatchers.IO) {
            SeriesSubscriptionManager.init(context)
            val activeSubs = SeriesSubscriptionManager.getAllActiveSubscriptions(context)
            if (activeSubs.isEmpty()) return@withContext

            val movielixApi = MovielixApi(context)

            for (sub in activeSubs) {
                try {
                    var latestSeason = sub.lastKnownSeason
                    var latestEpisode = sub.lastKnownEpisode
                    var latestEpTitle: String? = null

                    when (sub.engine) {
                        "almasmovie" -> {
                            val detail = AlmasMovieApi.getDetails(sub.id, "tvshow")
                            if (detail != null && detail.seasons.isNotEmpty()) {
                                latestSeason = detail.seasons.maxOfOrNull { it.season } ?: 1
                                val episodes = AlmasMovieApi.getEpisodes(sub.id, latestSeason)
                                val maxEp = episodes.maxByOrNull { it.episode }
                                if (maxEp != null) {
                                    latestEpisode = maxEp.episode
                                    latestEpTitle = maxEp.title
                                }
                            }
                        }
                        "nextmovie" -> {
                            val detail = NextMovieApi.getDetails(sub.id)
                            if (detail != null && detail.seasons.isNotEmpty()) {
                                latestSeason = detail.seasons.maxOfOrNull { it.season } ?: 1
                                val episodes = NextMovieApi.getEpisodes(sub.id, latestSeason)
                                val maxEp = episodes.maxByOrNull { it.episode }
                                if (maxEp != null) {
                                    latestEpisode = maxEp.episode
                                    latestEpTitle = maxEp.title
                                }
                            }
                        }
                        "bj" -> {
                            val detail = BjApi.getDetails(sub.id)
                            if (detail != null && detail.seasons.isNotEmpty()) {
                                latestSeason = detail.seasons.maxOfOrNull { it.season } ?: 1
                                val episodes = BjApi.getEpisodes(sub.id, latestSeason)
                                val maxEp = episodes.maxByOrNull { it.episode }
                                if (maxEp != null) {
                                    latestEpisode = maxEp.episode
                                    latestEpTitle = maxEp.title
                                }
                            }
                        }
                        "rezflix" -> {
                            val detail = RezFlixApi.getDetails(sub.id)
                            if (detail != null && detail.seasons.isNotEmpty()) {
                                latestSeason = detail.seasons.maxOfOrNull { it.season } ?: 1
                            }
                        }
                        else -> {

                            val detail = movielixApi.getMovieDetails(sub.id)
                            if (detail != null && detail.seasons.isNotEmpty()) {
                                latestSeason = detail.seasons.maxOfOrNull { it.season } ?: 1
                                val episodes = movielixApi.getEpisodes(sub.id, latestSeason)
                                val maxEp = episodes.maxByOrNull { it.episode }
                                if (maxEp != null) {
                                    latestEpisode = maxEp.episode
                                    latestEpTitle = maxEp.title
                                }
                            }
                        }
                    }

                    val isNew = (latestSeason > sub.lastKnownSeason) ||
                            (latestSeason == sub.lastKnownSeason && latestEpisode > sub.lastKnownEpisode)

                    if (isNew && latestEpisode > 0) {
                        AppLogger.i("SeriesUpdateWorker", "قسمت جدید برای «${sub.title}» کشف شد: فصل $latestSeason قسمت $latestEpisode")
                        SeriesNotificationHelper.sendNewEpisodeNotification(
                            context = context,
                            series = sub,
                            season = latestSeason,
                            episode = latestEpisode,
                            episodeTitle = latestEpTitle
                        )
                        SeriesSubscriptionManager.updateLastKnown(
                            context = context,
                            id = sub.id,
                            season = latestSeason,
                            episode = latestEpisode,
                            epTitle = latestEpTitle
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("SeriesUpdateWorker", "خطا در بررسی سریال «${sub.title}»: ${e.message}")
                }
            }
        }
    }
}
