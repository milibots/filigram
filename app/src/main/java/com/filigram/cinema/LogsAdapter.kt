package com.filigram.cinema

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.filigram.cinema.databinding.ItemLogEntryBinding

class LogsAdapter(
    private val logs: MutableList<AppLogger.LogEntry> = mutableListOf()
) : RecyclerView.Adapter<LogsAdapter.LogViewHolder>() {

    fun addLog(entry: AppLogger.LogEntry) {
        logs.add(entry)
        if (logs.size > 1000) {
            logs.removeAt(0)
            notifyItemRemoved(0)
        }
        notifyItemInserted(logs.size - 1)
    }

    fun setAllLogs(allLogs: List<AppLogger.LogEntry>) {
        logs.clear()
        logs.addAll(allLogs)
        notifyDataSetChanged()
    }

    fun clear() {
        logs.clear()
        notifyDataSetChanged()
    }

    inner class LogViewHolder(val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logs[position]
        holder.binding.logTimestamp.text = entry.timestamp
        holder.binding.logTag.text = "[${entry.tag}]"
        holder.binding.logMessage.text = entry.message

        val color = when (entry.level) {
            AppLogger.LogEntry.Level.SUCCESS -> Color.parseColor("#4CAF50")
            AppLogger.LogEntry.Level.WARN -> Color.parseColor("#FFB300")
            AppLogger.LogEntry.Level.ERROR -> Color.parseColor("#FF5252")
            AppLogger.LogEntry.Level.DEBUG -> Color.parseColor("#64B5F6")
            AppLogger.LogEntry.Level.INFO -> Color.parseColor("#E0E0E0")
        }
        holder.binding.logMessage.setTextColor(color)
    }

    override fun getItemCount(): Int = logs.size
}
