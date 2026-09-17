package me.phh.treble.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object DualWifiLogger {
    private const val BASE_TAG = "DualWifi"
    private const val MAX_LOG_ENTRIES = 300

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return "[$time] [$level] [$tag] $message"
        }
    }

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private fun addEntry(level: String, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        logEntries.add(entry)
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }
        _logsFlow.value = logEntries.toList()
    }

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        addEntry("VERB", tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        addEntry("DEBUG", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        addEntry("INFO", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        val text = if (tr != null) "$msg: ${tr.message}" else msg
        addEntry("WARN", tag, text)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        val text = if (tr != null) "$msg: ${tr.message}" else msg
        addEntry("ERROR", tag, text)
    }

    fun getAllLogs(): String {
        return logEntries.joinToString("\n") { it.formatted() }
    }

    fun clear() {
        logEntries.clear()
        _logsFlow.value = emptyList()
    }
}
