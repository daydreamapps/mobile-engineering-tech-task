package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserUiMapperTest {

    private val userA = UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active")
    private val userB = UserDto(2, "Alan Turing", "alan@example.com", "male", "active")

    @Test
    fun `each row's label reflects its own real added-at time, not its position in the list`() {
        val now = com.userhub.domain.time.TimeProvider.nowEpochMillis()
        val result = UsersResult.Success(
            users = listOf(userA, userB),
            addedAtMillis = mapOf(
                1L to now - 60 * 60_000, // 1 hour ago
                2L to now                // just now
            ),
            lastSyncMillis = null
        )

        val uiModels = result.toUiModels()

        assertEquals("1 hour ago", uiModels.first { it.id == 1L }.createdLabel)
        assertEquals("Just now", uiModels.first { it.id == 2L }.createdLabel)
        assertNotEquals(uiModels[0].createdLabel, uiModels[1].createdLabel)
    }

    @Test
    fun `a user missing from the added-at map still gets a label instead of crashing`() {
        val result = UsersResult.Success(users = listOf(userA), lastSyncMillis = null)

        val uiModels = result.toUiModels()

        assertEquals("Just now", uiModels.single().createdLabel)
    }
}
