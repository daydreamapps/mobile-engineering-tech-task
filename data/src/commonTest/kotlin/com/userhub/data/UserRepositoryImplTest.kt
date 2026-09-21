package com.userhub.data

import com.userhub.data.local.CachedUser
import com.userhub.data.remote.ApiConfig
import com.userhub.data.remote.GoRestApi
import com.userhub.data.remote.UserDto
import com.userhub.data.remote.createHttpClient
import com.userhub.data.repository.UserRepositoryImpl
import com.userhub.data.repository.UsersResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryImplTest {

    private val usersJson = """
        [
          {"id":1,"name":"Ada Lovelace","email":"ada@example.com","gender":"female","status":"active"},
          {"id":2,"name":"Alan Turing","email":"alan@example.com","gender":"male","status":"active"}
        ]
    """.trimIndent()

    private fun successEngine() = MockEngine {
        respond(
            content = usersJson,
            status = HttpStatusCode.OK,
            headers = headersOf(
                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                "x-pagination-pages" to listOf("3")
            )
        )
    }

    @Test
    fun `returns users from the last page`() = runTest {
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(successEngine())),
            FakeLocalDataSource()
        )

        val result = repository.getUsers()

        assertIs<UsersResult.Success>(result)
        assertEquals(2, result.users.size)
    }

    @Test
    fun `caches users after a successful fetch`() = runTest {
        val local = FakeLocalDataSource()
        val repository = UserRepositoryImpl(GoRestApi(createHttpClient(successEngine())), local)

        repository.getUsers()

        assertEquals(2, local.savedUsers.size)
        assertTrue(local.savedUsers.any { it.email == "ada@example.com" })
    }

    @Test
    fun `falls back to cached users when the network fails`() = runTest {
        val cached = listOf(
            CachedUser(UserDto(7, "Cached User", "cached@example.com", "male", "active"), 1_700_000_000_000L)
        )
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(engine)),
            FakeLocalDataSource(cached)
        )

        val result = repository.getUsers()

        assertIs<UsersResult.Success>(result)
        assertEquals("cached@example.com", result.users.single().email)
        assertEquals(1_700_000_000_000L, result.lastSyncMillis)
    }

    @Test
    fun `uses the public GoRest base url`() {
        assertTrue(ApiConfig.BASE_URL.startsWith("https://"))
    }

    // --- Workstream 1: empty-cache/offline crash ---

    @Test
    fun `returns NoInternet instead of throwing when the network fails and the cache is empty`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val repository = UserRepositoryImpl(
            GoRestApi(createHttpClient(engine)),
            FakeLocalDataSource() // empty cache
        )

        val result = repository.getUsers()

        assertIs<UsersResult.NoInternet>(result)
    }

    // --- Workstream 2: real "added ago" time (data-layer half) ---
    //
    // The "preserves a user's first-seen time across repeated successful fetches" test from
    // IMPLEMENTATION_PLAN.md is not included here: it asserts on `UsersResult.Success.addedAtMillis`,
    // which does not exist anywhere in production. There is no way to express this test against
    // today's code without first adding that field/capability to production — which is out of
    // scope for this branch (tests only, no fixes). See PROCESS_LOG.md.

    @Test
    fun `drops cached users that are no longer present in the latest fetch`() = runTest {
        val local = FakeLocalDataSource(
            initial = listOf(
                CachedUser(UserDto(99, "Stale User", "stale@example.com", "male", "active"), cachedAt = 1L)
            )
        )
        val repository = UserRepositoryImpl(GoRestApi(createHttpClient(successEngine())), local)

        repository.getUsers()

        assertTrue(local.getUsers().none { it.user.id == 99L })
    }
}
