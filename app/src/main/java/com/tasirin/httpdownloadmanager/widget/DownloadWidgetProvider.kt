package com.tasirin.httpdownloadmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tasirin.httpdownloadmanager.App
import com.tasirin.httpdownloadmanager.MainActivity
import com.tasirin.httpdownloadmanager.R
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.util.Formats
import java.util.Locale

class DownloadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, App.engine.items.value)
    }

    companion object {

        private val ROW_IDS = intArrayOf(R.id.row1, R.id.row2, R.id.row3)
        private val TITLE_IDS = intArrayOf(R.id.row1_title, R.id.row2_title, R.id.row3_title)
        private val INFO_IDS = intArrayOf(R.id.row1_info, R.id.row2_info, R.id.row3_info)
        private val BAR_IDS = intArrayOf(R.id.row1_bar, R.id.row2_bar, R.id.row3_bar)

        fun update(context: Context, items: List<DownloadItem>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DownloadWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val active = items.filter {
                it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
            }
            val views = RemoteViews(context.packageName, R.layout.widget_download)
            val openIntent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            if (active.isEmpty()) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle))
            } else {
                views.setTextViewText(
                    R.id.widget_title,
                    context.getString(R.string.widget_active, active.size)
                )
            }

            for (i in 0 until 3) {
                val show = i < active.size
                views.setViewVisibility(ROW_IDS[i], if (show) View.VISIBLE else View.GONE)
                if (show) {
                    val item = active[i]
                    views.setTextViewText(TITLE_IDS[i], item.fileName)
                    views.setTextViewText(INFO_IDS[i], infoText(item))
                    views.setProgressBar(BAR_IDS[i], 100, item.progressPercent, false)
                }
            }
            manager.updateAppWidget(ids, views)
        }

        private fun infoText(item: DownloadItem): String {
            return if (item.totalBytes > 0) {
                String.format(
                    Locale.US, "%d%% \u2022 %s / %s",
                    item.progressPercent,
                    Formats.bytes(item.bytesDownloaded),
                    Formats.bytes(item.totalBytes)
                )
            } else {
                Formats.bytes(item.bytesDownloaded)
            }
        }

    }
}
