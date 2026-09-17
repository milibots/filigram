package com.filigram.cinema.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.filigram.cinema.MainActivity
import com.filigram.cinema.R

class DownloadForegroundService : Service(), DownloadManager.DownloadListener {

    private val CHANNEL_ID = "filigram_downloads_channel"
    private val NOTIFICATION_ID = 4001
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START = "com.filigram.cinema.action.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP = "com.filigram.cinema.action.STOP_DOWNLOAD_SERVICE"
        const val ACTION_PAUSE_ALL = "com.filigram.cinema.action.PAUSE_ALL"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        DownloadManager.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_ALL -> {
                DownloadManager.pauseAll(this)
            }
            else -> {
                startForegroundNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        DownloadManager.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "دانلودهای فیلیگرام",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش سرعت و وضعیت دانلود فیلم‌ها و سریال‌ها"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification(0L, DownloadManager.getActiveDownloadsCount())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(speed: Long, activeCount: Int): Notification {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseAllIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_PAUSE_ALL
        }
        val pauseAllPending = PendingIntent.getService(
            this,
            1,
            pauseAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speedText = DownloadManager.formatSpeed(speed)
        val titleText = if (activeCount > 0) {
            "در حال دانلود ($activeCount مورد فعال) — $speedText"
        } else {
            "دانلودهای فیلیگرام (در انتظار)"
        }

        val activeTask = DownloadManager.getTasks().firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        val contentText = if (activeTask != null) {
            "${activeTask.title} (${activeTask.progressPercent}%)"
        } else {
            "تمام دانلودها به پایان رسید یا متوقف است"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_vector)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(activeCount > 0)
            .setOnlyAlertOnce(true)

        if (activeCount > 0) {
            builder.addAction(android.R.drawable.ic_media_pause, "توقف همه", pauseAllPending)
            if (activeTask != null && activeTask.totalBytes > 0) {
                builder.setProgress(100, activeTask.progressPercent, false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }

        return builder.build()
    }

    override fun onTaskUpdated(task: DownloadTask) {
        val count = DownloadManager.getActiveDownloadsCount()
        if (count == 0) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(0L, 0))
        }
    }

    override fun onQueueChanged() {
        val count = DownloadManager.getActiveDownloadsCount()
        if (count == 0 && DownloadManager.getTasks().none { it.status == DownloadStatus.QUEUED }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onTotalSpeedUpdated(totalBytesPerSec: Long, activeCount: Int) {
        if (activeCount > 0) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(totalBytesPerSec, activeCount))
        }
    }
}
