package com.userhub.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("gender") val gender: String,
    @SerialName("status") val status: String
)
