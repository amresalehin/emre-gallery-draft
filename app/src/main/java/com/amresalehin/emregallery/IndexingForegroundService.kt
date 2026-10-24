package com.amresalehin.emregallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app process in foreground priority while the UI-owned OCR/face-indexing
 * jobs are running, and exposes their progress in the system notification shade.
 */
class IndexingForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress for OCR and face indexing"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Media indexing"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Preparing…"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1
        val max = intent?.getIntExtra(EXTRA_MAX, 0) ?: 0
        val notification = buildNotification(title, message, progress, max)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, message: String, progress: Int, max: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress >= 0 && max > 0) {
            builder.setProgress(max, progress.coerceIn(0, max), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "media_indexing"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_UPDATE = "com.amresalehin.emregallery.indexing.UPDATE"
        private const val ACTION_STOP = "com.amresalehin.emregallery.indexing.STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_MAX = "max"

        fun startOrUpdate(context: Context, title: String, message: String, progress: Int = -1, max: Int = 0) {
            val intent = Intent(context, IndexingForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX, max)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IndexingForegroundService::class.java))
        }
    }
}
