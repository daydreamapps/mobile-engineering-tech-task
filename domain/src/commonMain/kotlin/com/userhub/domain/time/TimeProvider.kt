package com.userhub.domain.time

object TimeProvider {
    fun nowEpochMillis(): Long = systemEpochMillis() + utcOffsetMillis()
}

internal expect fun systemEpochMillis(): Long

internal expect fun utcOffsetMillis(): Long
