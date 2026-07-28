package com.userhub.data.local

import com.userhub.data.db.UserDatabase
import com.userhub.data.remote.UserDto

class SqlDelightUserLocalDataSource(database: UserDatabase) : UserLocalDataSource {

    private val queries = database.userCacheQueries

    override fun saveUsers(users: List<UserDto>, timestamp: Long) {
        queries.transaction {
            queries.clearAll()
            users.forEach { user ->
                queries.insertUser(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    gender = user.gender,
                    status = user.status,
                    cached_at = timestamp
                )
            }
        }
    }

    override fun getUsers(): List<CachedUser> =
        queries.selectAll().executeAsList().map { row ->
            CachedUser(
                user = UserDto(
                    id = row.id,
                    name = row.name,
                    email = row.email,
                    gender = row.gender,
                    status = row.status
                ),
                cachedAt = row.cached_at
            )
        }
}
