package com.userhub.domain.time

import java.util.TimeZone

internal actual fun systemEpochMillis(): Long = System.currentTimeMillis()

internal actual fun utcOffsetMillis(): Long =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
