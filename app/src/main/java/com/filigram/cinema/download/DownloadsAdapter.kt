package com.filigram.cinema.download

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.R
import com.filigram.cinema.databinding.ItemDownloadCardBinding

class DownloadsAdapter(
    private val onPauseResumeClick: (DownloadTask) -> Unit,
    private val onCancelDeleteClick: (DownloadTask) -> Unit,
    private val onPlayDownloadedClick: (DownloadTask) -> Unit
) : ListAdapter<DownloadTask, DownloadsAdapter.DownloadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            holder.bindProgressOnly(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class DownloadViewHolder(val binding: ItemDownloadCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: DownloadTask) {
            binding.tvDownloadTitle.text = task.title
            binding.tvQualityBadge.text = task.qualityLabel.ifEmpty { "HQ" }
            binding.tvPartsBadge.text = "${task.partsCount} تکه موازی"

            updateStatusAndSpeed(task)

            binding.btnPauseResume.setOnClickListener { onPauseResumeClick(task) }
            binding.btnCancelDelete.setOnClickListener { onCancelDeleteClick(task) }
            binding.btnPlayDownloaded.setOnClickListener { onPlayDownloadedClick(task) }
        }

        fun bindProgressOnly(task: DownloadTask) {
            updateStatusAndSpeed(task)
        }

        private fun updateStatusAndSpeed(task: DownloadTask) {
            val percent = task.progressPercent
            binding.progressDownload.progress = percent
            binding.progressDownload.isIndeterminate = task.status == DownloadStatus.CONNECTING

            val downloadedStr = DownloadManager.formatBytes(task.downloadedBytes)
            val totalStr = if (task.totalBytes > 0) DownloadManager.formatBytes(task.totalBytes) else "..."
            binding.tvSizeProgress.text = "$downloadedStr / $totalStr ($percent%)"

            when (task.status) {
                DownloadStatus.DOWNLOADING -> {
                    binding.tvStatusBadge.text = "در حال دریافت"
                    binding.tvStatusBadge.setTextColor(0xFF00E560.toInt())
                    val speedStr = DownloadManager.formatSpeed(task.speedBytesPerSec)
                    val etaStr = if (task.speedBytesPerSec > 0 && task.totalBytes > task.downloadedBytes) {
                        val remainingBytes = task.totalBytes - task.downloadedBytes
                        val remainingSec = remainingBytes / task.speedBytesPerSec
                        val min = remainingSec / 60
                        val sec = remainingSec % 60
                        " — $min:$sec باقی‌مانده"
                    } else ""
                    binding.tvSpeedEta.text = "$speedStr$etaStr"

                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    binding.layoutCompletedAction.isVisible = false
                }
                DownloadStatus.CONNECTING -> {
                    binding.tvStatusBadge.text = "اتصال به سرور..."
                    binding.tvStatusBadge.setTextColor(0xFFFFA000.toInt())
                    binding.tvSpeedEta.text = "در حال آماده‌سازی چندتکه..."
                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    binding.layoutCompletedAction.isVisible = false
                }
                DownloadStatus.QUEUED -> {
                    binding.tvStatusBadge.text = "در صف دانلود"
                    binding.tvStatusBadge.setTextColor(0xFF888888.toInt())
                    binding.tvSpeedEta.text = "در انتظار ظرفیت خالی..."
                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    binding.layoutCompletedAction.isVisible = false
                }
                DownloadStatus.PAUSED -> {
                    binding.tvStatusBadge.text = "متوقف شده"
                    binding.tvStatusBadge.setTextColor(0xFFFFA000.toInt())
                    binding.tvSpeedEta.text = "توقف موقت"
                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    binding.layoutCompletedAction.isVisible = false
                }
                DownloadStatus.COMPLETED -> {
                    binding.tvStatusBadge.text = "تکمیل شده ✓"
                    binding.tvStatusBadge.setTextColor(0xFF00E560.toInt())
                    binding.tvSpeedEta.text = "ذخیره در پوشه دانلودها"
                    binding.btnPauseResume.isVisible = false
                    binding.layoutCompletedAction.isVisible = true
                }
                DownloadStatus.FAILED -> {
                    binding.tvStatusBadge.text = "خطا در دانلود"
                    binding.tvStatusBadge.setTextColor(0xFFFF4444.toInt())
                    val err = task.errorMessage ?: "خطای ناشناخته در اتصال"
                    binding.tvSpeedEta.text = if (err.contains("connect", ignoreCase = true) || err.contains("timeout", ignoreCase = true) || err.contains("ms")) {
                        "خطای شبکه یا مسدودی سرور (روی پلی بزنید)"
                    } else {
                        err
                    }
                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    binding.layoutCompletedAction.isVisible = false
                }
                DownloadStatus.CANCELLED -> {
                    binding.tvStatusBadge.text = "لغو شده"
                    binding.tvStatusBadge.setTextColor(0xFF888888.toInt())
                    binding.tvSpeedEta.text = "لغو توسط کاربر"
                    binding.btnPauseResume.isVisible = true
                    binding.btnPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    binding.layoutCompletedAction.isVisible = false
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.status == newItem.status &&
                    oldItem.downloadedBytes == newItem.downloadedBytes &&
                    oldItem.speedBytesPerSec == newItem.speedBytesPerSec
        }

        override fun getChangePayload(oldItem: DownloadTask, newItem: DownloadTask): Any? {
            return if (oldItem.id == newItem.id) true else null
        }
    }
}
