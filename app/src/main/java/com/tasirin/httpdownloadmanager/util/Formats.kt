package com.tasirin.httpdownloadmanager.util

import java.util.Locale

object Formats {

    fun bytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun speed(bps: Long): String {
        if (bps < 1024) return "$bps B/s"
        val kb = bps / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
        return String.format(Locale.US, "%.2f MB/s", kb / 1024.0)
    }

    fun eta(seconds: Long): String {
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
}
