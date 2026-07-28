package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.domain.time.TimeProvider
import com.userhub.domain.time.formatRelativeTime

private const val FEED_STAGGER_MILLIS = 5 * 60 * 1_000L

fun List<UserDto>.toUiModels(): List<UserUiModel> {
    val now = TimeProvider.nowEpochMillis()
    return mapIndexed { index, user ->
        val addedAt = now - index * FEED_STAGGER_MILLIS
        UserUiModel(user = user, createdLabel = formatRelativeTime(addedAt, now))
    }
}

fun UserDto.toNewUiModel(): UserUiModel {
    val now = TimeProvider.nowEpochMillis()
    return UserUiModel(user = this, createdLabel = formatRelativeTime(now, now))
}
