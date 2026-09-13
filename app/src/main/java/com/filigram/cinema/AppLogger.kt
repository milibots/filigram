package com.filigram.cinema

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogger {

    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        enum class Level {
            INFO, SUCCESS, WARN, ERROR, DEBUG
        }
    }

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(level: LogEntry.Level, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        logs.add(entry)
        if (logs.size > 1000) {
            logs.removeAt(0)
        }
        listeners.forEach { listener ->
            try {
                listener(entry)
            } catch (e: Exception) {
                // ignore listener errors
            }
        }
    }

    fun i(tag: String, msg: String) = log(LogEntry.Level.INFO, tag, msg)
    fun s(tag: String, msg: String) = log(LogEntry.Level.SUCCESS, tag, msg)
    fun w(tag: String, msg: String) = log(LogEntry.Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(LogEntry.Level.ERROR, tag, msg)
    fun d(tag: String, msg: String) = log(LogEntry.Level.DEBUG, tag, msg)

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun clear() {
        logs.clear()
        i("Logger", "لاگ‌ها پاکسازی شدند.")
    }
}
