package com.userhub.data

import com.userhub.data.local.CachedUser
import com.userhub.data.local.UserLocalDataSource
import com.userhub.data.remote.UserDto

class FakeLocalDataSource(initial: List<CachedUser> = emptyList()) : UserLocalDataSource {

    private var stored = initial.toMutableList()

    var savedUsers: List<UserDto> = emptyList()
        private set

    override fun saveUsers(users: List<UserDto>, timestamp: Long): List<CachedUser> {
        savedUsers = users
        val previousFirstSeen = stored.associate { it.user.id to it.firstSeenAt }
        val updated = users.map { user ->
            CachedUser(
                user = user,
                cachedAt = timestamp,
                firstSeenAt = previousFirstSeen[user.id] ?: timestamp
            )
        }
        stored = updated.toMutableList()
        return stored
    }

    override fun getUsers(): List<CachedUser> = stored
}
