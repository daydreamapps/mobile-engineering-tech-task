package com.userhub.domain.time

expect fun formatRelativeTime(epochMillis: Long, nowMillis: Long): String
