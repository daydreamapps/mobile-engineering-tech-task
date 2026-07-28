package com.userhub.domain.time

actual fun formatRelativeTime(epochMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - epochMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 120 -> "$minutes minutes ago"
        minutes < 1_440 -> {
            val hours = (minutes + 30) / 60
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        minutes < 2_880 -> "Yesterday"
        else -> "${minutes / 1_440} days ago"
    }
}
