package com.tasirin.httpdownloadmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tasirin.httpdownloadmanager.R
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.util.Formats
import com.tasirin.httpdownloadmanager.databinding.ItemDownloadBinding
import java.io.File
import java.util.Locale

class DownloadAdapter(private val listener: Listener) :
    ListAdapter<DownloadItem, DownloadAdapter.ViewHolder>(DIFF) {

    enum class Action { PAUSE, RESUME, CANCEL, DELETE, OPEN, OPEN_FOLDER }

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

        val quick = b.buttonQuick
        val quickResume = item.state == DownloadState.PAUSED || item.state == DownloadState.FAILED
        val quickPause = item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PENDING
        if (quickResume || quickPause) {
            quick.visibility = View.VISIBLE
            quick.setIconResource(if (quickResume) R.drawable.ic_play else R.drawable.ic_pause)
            quick.contentDescription = b.root.context.getString(
                if (quickResume) R.string.resume else R.string.pause
            )
            quick.setOnClickListener {
                listener.onAction(item, if (quickResume) Action.RESUME else Action.PAUSE)
            }
        } else {
            quick.visibility = View.GONE
        }
        b.textProgress.text = if (item.totalBytes > 0) {
            String.format(
                Locale.US, "%d%% \u2022 %s / %s",
                item.progressPercent,
                Formats.bytes(item.bytesDownloaded),
                Formats.bytes(item.totalBytes)
            )
        } else {
            Formats.bytes(item.bytesDownloaded)
        }

        val showSpeed = item.state == DownloadState.DOWNLOADING && item.speedBps > 0
        b.textSpeed.visibility = if (showSpeed) View.VISIBLE else View.GONE
        if (showSpeed) {
            b.textSpeed.text = b.root.context.getString(
                R.string.speed_and_eta,
                Formats.speed(item.speedBps),
                Formats.eta(item.etaSeconds)
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

        b.buttonActions.setOnClickListener { v ->
            val menu = PopupMenu(v.context, v)
            menu.menuInflater.inflate(R.menu.item_actions, menu.menu)
            val m = menu.menu
            m.findItem(R.id.action_item_pause).isVisible =
                item.state == DownloadState.DOWNLOADING
            m.findItem(R.id.action_item_resume).isVisible =
                item.state == DownloadState.PAUSED || item.state == DownloadState.FAILED
            m.findItem(R.id.action_item_open).isVisible =
                item.state == DownloadState.COMPLETED
            m.findItem(R.id.action_item_folder).isVisible =
                item.state == DownloadState.COMPLETED
            m.findItem(R.id.action_item_cancel).isVisible =
                item.state == DownloadState.DOWNLOADING ||
                    item.state == DownloadState.PENDING ||
                    item.state == DownloadState.PAUSED
            m.findItem(R.id.action_item_delete).isVisible =
                item.state == DownloadState.COMPLETED ||
                    item.state == DownloadState.FAILED ||
                    item.state == DownloadState.CANCELLED
            menu.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    R.id.action_item_pause -> listener.onAction(item, Action.PAUSE)
                    R.id.action_item_resume -> listener.onAction(item, Action.RESUME)
                    R.id.action_item_cancel -> listener.onAction(item, Action.CANCEL)
                    R.id.action_item_open -> listener.onAction(item, Action.OPEN)
                    R.id.action_item_folder -> listener.onAction(item, Action.OPEN_FOLDER)
                    R.id.action_item_delete -> listener.onAction(item, Action.DELETE)
                }
                true
            }
            menu.show()
        }
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

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem) =
                oldItem == newItem
        }
    }
}
