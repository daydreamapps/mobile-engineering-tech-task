package com.userhub.presentation

import com.userhub.data.remote.UserDto

data class UserUiModel(
    val user: UserDto,
    val createdLabel: String
) {
    val id: Long get() = user.id
    val name: String get() = user.name
    val email: String get() = user.email
}
