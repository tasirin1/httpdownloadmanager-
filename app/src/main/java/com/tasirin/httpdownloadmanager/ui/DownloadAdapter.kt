package com.tasirin.httpdownloadmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tasirin.httpdownloadmanager.R
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.databinding.ItemDownloadBinding
import java.io.File
import java.util.Locale

class DownloadAdapter(private val listener: Listener) :
    ListAdapter<DownloadItem, DownloadAdapter.ViewHolder>(DIFF) {

    enum class Action { PAUSE, RESUME, CANCEL, DELETE, OPEN }

    interface Listener {
        fun onAction(item: DownloadItem, action: Action)
        fun onLongPress(item: DownloadItem)
    }

    class ViewHolder(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.textName.text = item.fileName
        b.textStatus.text = statusText(item, b.root.context)
        b.progressBar.max = 100
        b.progressBar.progress = item.progressPercent
        b.textProgress.text = if (item.totalBytes > 0) {
            String.format(
                Locale.US, "%d%% \u2022 %s / %s",
                item.progressPercent,
                formatSize(item.bytesDownloaded),
                formatSize(item.totalBytes)
            )
        } else {
            formatSize(item.bytesDownloaded)
        }

        val showSpeed = item.state == DownloadState.DOWNLOADING && item.speedBps > 0
        b.textSpeed.visibility = if (showSpeed) View.VISIBLE else View.GONE
        if (showSpeed) {
            b.textSpeed.text = b.root.context.getString(
                R.string.speed_and_eta,
                formatSpeed(item.speedBps),
                formatEta(item.etaSeconds)
            )
        }

        b.textChecksumOk.visibility =
            if (item.state == DownloadState.COMPLETED && item.checksumVerified) {
                View.VISIBLE
            } else {
                View.GONE
            }
        if (item.state == DownloadState.COMPLETED) {
            val context = b.root.context
            val location = when {
                !item.filePath.isNullOrEmpty() -> {
                    File(item.filePath).parent ?: item.filePath
                }
                !item.contentUri.isNullOrEmpty() ->
                    context.getString(R.string.location_media_store)
                else -> context.getString(R.string.location_internal)
            }
            b.textLocation.text = context.getString(R.string.location_label, location)
            b.textLocation.visibility = View.VISIBLE
        } else {
            b.textLocation.visibility = View.GONE
        }

        b.buttonPause.visibility =
            if (item.state == DownloadState.DOWNLOADING) View.VISIBLE else View.GONE
        b.buttonResume.visibility =
            if (item.state == DownloadState.PAUSED || item.state == DownloadState.FAILED) {
                View.VISIBLE
            } else {
                View.GONE
            }
        b.buttonOpen.visibility =
            if (item.state == DownloadState.COMPLETED) View.VISIBLE else View.GONE
        b.buttonCancel.visibility =
            if (item.state == DownloadState.DOWNLOADING ||
                item.state == DownloadState.PENDING ||
                item.state == DownloadState.PAUSED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        b.buttonDelete.visibility =
            if (item.state == DownloadState.COMPLETED ||
                item.state == DownloadState.FAILED ||
                item.state == DownloadState.CANCELLED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        b.buttonPause.setOnClickListener { listener.onAction(item, Action.PAUSE) }
        b.buttonResume.setOnClickListener { listener.onAction(item, Action.RESUME) }
        b.buttonCancel.setOnClickListener { listener.onAction(item, Action.CANCEL) }
        b.buttonOpen.setOnClickListener { listener.onAction(item, Action.OPEN) }
        b.buttonDelete.setOnClickListener { listener.onAction(item, Action.DELETE) }
        b.root.setOnLongClickListener {
            listener.onLongPress(item)
            true
        }
    }

    private fun statusText(item: DownloadItem, context: android.content.Context): String {
        return when (item.state) {
            DownloadState.PENDING -> context.getString(R.string.status_pending)
            DownloadState.DOWNLOADING -> context.getString(R.string.status_downloading)
            DownloadState.PAUSED -> context.getString(R.string.status_paused)
            DownloadState.COMPLETED -> context.getString(R.string.status_completed)
            DownloadState.CANCELLED -> context.getString(R.string.status_cancelled)
            DownloadState.FAILED -> item.error ?: context.getString(R.string.status_failed)
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun formatSpeed(bps: Long): String {
        if (bps < 1024) return "$bps B/s"
        val kb = bps / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
        return String.format(Locale.US, "%.2f MB/s", kb / 1024.0)
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
                oldItem == newItem
        }
    }
}
