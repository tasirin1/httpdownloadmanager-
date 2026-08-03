package com.tasirin.downloadmanager.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.tasirin.downloadmanager.MainActivity
import com.tasirin.downloadmanager.R
import com.tasirin.downloadmanager.data.DownloadItem
import com.tasirin.downloadmanager.data.DownloadState

object NotificationHelper {

    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun foregroundNotification(context: Context): Notification = buildNotification(context, emptyList())

    fun updateNotification(context: Context, items: List<DownloadItem>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(context, items))
    }

    private fun buildNotification(context: Context, items: List<DownloadItem>): Notification {
        val active = items.filter {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
        }
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setOngoing(active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)

        if (active.isNotEmpty()) {
            val totalBytes = active.sumOf { it.totalBytes }
            val downloadedBytes = active.sumOf { it.bytesDownloaded }
            builder.setContentText(context.getString(R.string.notification_active_files, active.size))
            if (totalBytes > 0) {
                builder.setProgress(100, (downloadedBytes * 100 / totalBytes).toInt(), false)
            } else {
                builder.setProgress(0, 0, true)
            }
        } else {
            val completed = items.count { it.state == DownloadState.COMPLETED }
            builder.setContentText(context.getString(R.string.notification_done, completed))
                .setProgress(0, 0, false)
        }
        return builder.build()
    }
}
