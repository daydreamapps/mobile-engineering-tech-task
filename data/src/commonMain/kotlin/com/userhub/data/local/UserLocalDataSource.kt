package com.userhub.data.local

import com.userhub.data.remote.UserDto

data class CachedUser(
    val user: UserDto,
    val cachedAt: Long,
    val firstSeenAt: Long
)

interface UserLocalDataSource {
    fun saveUsers(users: List<UserDto>, timestamp: Long): List<CachedUser>
    fun getUsers(): List<CachedUser>
}
