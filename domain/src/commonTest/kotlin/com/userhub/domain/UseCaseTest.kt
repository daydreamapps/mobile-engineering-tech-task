package com.userhub.domain

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import com.userhub.domain.usecase.AddUserUseCase
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UseCaseTest {

    private val sampleUsers = listOf(
        UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active"),
        UserDto(2, "Alan Turing", "alan@example.com", "male", "active")
    )

    @Test
    fun `get users delegates to the repository once`() = runTest {
        val repository = FakeUserRepository(sampleUsers)

        val result = GetUsersUseCase(repository).invoke()

        assertEquals(1, repository.getUsersCalls)
        assertIs<UsersResult.Success>(result)
    }

    @Test
    fun `add user forwards name and email to the repository`() = runTest {
        val repository = FakeUserRepository()

        val response = AddUserUseCase(repository).invoke("Grace Hopper", "grace@example.com")

        assertEquals("Grace Hopper", repository.createdName)
        assertEquals("grace@example.com", repository.createdEmail)
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `delete user forwards the identifier to the repository`() = runTest {
        val repository = FakeUserRepository()

        val response = DeleteUserUseCase(repository).invoke(42L)

        assertEquals(listOf(42L), repository.deletedIds)
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
