package com.amresalehin.emregallery.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object DownloadActivityFormatters {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2fGB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.2fMB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1fKB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(speedBytesPerSec: Long): String {
        if (speedBytesPerSec <= 0) return "0.00MB/s"
        val mb = speedBytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 0.05) {
            String.format(Locale.US, "%.2fMB/s", mb)
        } else {
            val kb = speedBytesPerSec / 1024.0
            String.format(Locale.US, "%.1fKB/s", kb)
        }
    }

    fun formatDate(timestampMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMillis
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestampMillis))

        return when {
            diff < 24 * 3600 * 1000L -> "Today, $timeStr"
            diff < 48 * 3600 * 1000L -> "Yesterday, $timeStr"
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                dateFormat.format(Date(timestampMillis))
            }
        }
    }
}
