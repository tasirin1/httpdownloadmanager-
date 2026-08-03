package com.tasirin.downloadmanager.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tasirin.downloadmanager.App
import com.tasirin.downloadmanager.data.DownloadState
import com.tasirin.downloadmanager.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        startForegroundCompat()
        scope.launch {
            App.engine.items.collect { items ->
                val active = items.any {
                    it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
                }
                NotificationHelper.updateNotification(this@DownloadService, items)
                if (!active) {
                    ServiceCompat.stopForeground(
                        this@DownloadService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startForegroundCompat() {
        val notification = NotificationHelper.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
