package com.userhub.presentation

import com.userhub.data.remote.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals

class UserUiMapperTest {

    // The plan's original version of this test asserted on `UsersResult.Success.addedAtMillis`,
    // which does not exist in production (see PROCESS_LOG.md — tests only, no fixes on this
    // branch). Rewritten to demonstrate the same underlying bug through the API that actually
    // exists today: `List<UserDto>.toUiModels()` derives each row's "added ago" label purely
    // from its position in the list, not from any real per-user signal.

    private val userA = UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active")
    private val userB = UserDto(2, "Alan Turing", "alan@example.com", "male", "active")

    @Test
    fun `two users with no real difference in when they were added still get different labels, purely from list position`() {
        val uiModels = listOf(userA, userB).toUiModels()

        // Nothing distinguishes userA from userB except their position in the input list — there
        // is no real "added at" signal being used at all — so there is no honest basis for their
        // labels to differ.
        assertEquals(uiModels[0].createdLabel, uiModels[1].createdLabel)
    }
}
