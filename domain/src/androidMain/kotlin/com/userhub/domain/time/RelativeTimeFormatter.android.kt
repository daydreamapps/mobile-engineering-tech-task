package com.userhub.domain.time

actual fun formatRelativeTime(epochMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - epochMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 120 -> "1 hour ago"
        minutes < 1_440 -> "${minutes / 60} hours ago"
        minutes < 2_880 -> "1 day ago"
        else -> "${minutes / 1_440} days ago"
    }
}
