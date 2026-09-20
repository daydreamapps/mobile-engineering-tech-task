package com.userhub.presentation

import com.userhub.data.remote.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectedUserResolverTest {

    private val userA = UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active").toNewUiModel()
    private val userB = UserDto(2, "Alan Turing", "alan@example.com", "male", "active").toNewUiModel()

    @Test
    fun `returns the user matching the selected id`() {
        assertEquals(userB, resolveSelectedUser(listOf(userA, userB), selectedId = userB.id))
    }

    @Test
    fun `falls back to the first user when the selected id is no longer in the list`() {
        // simulates: userB was selected, then deleted
        assertEquals(userA, resolveSelectedUser(listOf(userA), selectedId = userB.id))
    }

    @Test
    fun `returns null when the list is empty`() {
        assertNull(resolveSelectedUser(emptyList(), selectedId = userA.id))
    }
}
