package com.videodownloader.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.videodownloader.app.databinding.ItemQueueDownloadBinding

class QueueItemAdapter(
    private val onRemove: (QueuedDownload) -> Unit,
) : ListAdapter<QueuedDownload, QueueItemAdapter.QueueViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return QueueViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class QueueViewHolder(
        private val binding: ItemQueueDownloadBinding,
        private val onRemove: (QueuedDownload) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueuedDownload) {
            val context = binding.root.context
            binding.queueUrlText.text = item.url
            binding.queueStatusText.text = when (item.status) {
                QueueStatus.PENDING -> context.getString(R.string.queue_status_pending)
                QueueStatus.DOWNLOADING -> {
                    if (item.statusMessage.isNotBlank()) {
                        item.statusMessage
                    } else {
                        context.getString(R.string.queue_status_downloading, item.progress)
                    }
                }
                QueueStatus.COMPLETED -> context.getString(R.string.queue_status_completed)
                QueueStatus.FAILED -> item.errorMessage ?: context.getString(R.string.download_failed)
                QueueStatus.CANCELLED -> context.getString(R.string.queue_status_cancelled)
            }

            binding.queueProgress.isVisible = item.status == QueueStatus.DOWNLOADING
            binding.queueProgress.progress = item.progress

            val colorRes = when (item.status) {
                QueueStatus.PENDING -> android.R.color.darker_gray
                QueueStatus.DOWNLOADING -> R.color.md_theme_primary
                QueueStatus.COMPLETED -> android.R.color.holo_green_dark
                QueueStatus.FAILED -> android.R.color.holo_red_dark
                QueueStatus.CANCELLED -> android.R.color.holo_orange_dark
            }
            binding.queueStatusText.setTextColor(
                ContextCompat.getColor(context, colorRes),
            )

            binding.removeQueueItemButton.isEnabled = true
            binding.removeQueueItemButton.setOnClickListener { onRemove(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<QueuedDownload>() {
        override fun areItemsTheSame(oldItem: QueuedDownload, newItem: QueuedDownload): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: QueuedDownload, newItem: QueuedDownload): Boolean {
            return oldItem == newItem
        }
    }
}
