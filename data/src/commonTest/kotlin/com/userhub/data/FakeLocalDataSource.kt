package com.userhub.data

import com.userhub.data.local.CachedUser
import com.userhub.data.local.UserLocalDataSource
import com.userhub.data.remote.UserDto

class FakeLocalDataSource(initial: List<CachedUser> = emptyList()) : UserLocalDataSource {

    private var stored = initial.toMutableList()

    var savedUsers: List<UserDto> = emptyList()
        private set

    override fun saveUsers(users: List<UserDto>, timestamp: Long) {
        savedUsers = users
        stored = users.map { CachedUser(it, timestamp) }.toMutableList()
    }

    override fun getUsers(): List<CachedUser> = stored
}
