package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserFeedViewModelTest {

    private val users = listOf(
        UserDto(1, "Ada Lovelace", "ada@example.com", "female", "active"),
        UserDto(2, "Alan Turing", "alan@example.com", "male", "active"),
        UserDto(3, "Grace Hopper", "grace@example.com", "female", "active")
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeUserRepository) = UserFeedViewModel(
        GetUsersUseCase(repository),
        DeleteUserUseCase(repository)
    )

    @Test
    fun `loads users from the repository on init`() = runTest {
        val repository = FakeUserRepository(users)

        val viewModel = viewModel(repository)

        assertEquals(1, repository.getUsersCalls)
        assertIs<UserFeedUiState.Content>(viewModel.state.value)
    }

    @Test
    fun `content state exposes every user returned by the repository`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(3, state.users.size)
        assertTrue(state.users.any { it.email == "grace@example.com" })
    }

    @Test
    fun `delete removes the user from the list`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))
        val target = (viewModel.state.value as UserFeedUiState.Content).users.first()

        viewModel.onDeleteConfirmed(target)

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(2, state.users.size)
        assertTrue(state.users.none { it.id == target.id })
    }

    @Test
    fun `undo restores the deleted user`() = runTest {
        val viewModel = viewModel(FakeUserRepository(users))
        val target = (viewModel.state.value as UserFeedUiState.Content).users.first()
        viewModel.onDeleteConfirmed(target)

        viewModel.onUndo()

        val state = viewModel.state.value
        assertIs<UserFeedUiState.Content>(state)
        assertEquals(3, state.users.size)
        assertEquals(target.id, state.users.first().id)
    }

    // --- Workstream 3 (undo/delete timing) — no test added here, deliberately ---
    //
    // Missing coverage: a test confirming that `onDeleteConfirmed()` does not call
    // `UserRepository.deleteUser()` immediately, and that the delete is only sent once the
    // undo window has actually elapsed (per IMPLEMENTATION_PLAN.md, Workstream 3).
    //
    // Why it isn't here: today's `onDeleteConfirmed()` fires the delete via
    // `GlobalScope.launch { ... }`, which runs on `Dispatchers.Default` — a real background
    // thread, not the `UnconfinedTestDispatcher` this test class installs on `Dispatchers.Main`.
    // `runTest` only awaits coroutines that are children of its own test scope; a `GlobalScope`
    // coroutine is not one, so a naive test (call `onDeleteConfirmed()`, then immediately assert
    // the fake repository's `deleteUser()` hasn't been called) would race against real background
    // I/O rather than deterministically observing the bug. Such a test would very likely pass
    // today regardless of whether the bug is present or fixed — the background coroutine simply
    // wouldn't have had time to run yet, either way — so it wouldn't actually be capable of
    // failing on the thing it's meant to catch. Writing something deterministic here would
    // require either (a) a hook like `onUndoWindowElapsed()` that does not exist in production
    // yet, or (b) making the fake repository's `deleteUser()` suspend on a manually-releasable
    // signal so the test can control the race itself — both are shaping the test double around
    // the intended fix rather than just proving the current bug, so left out of scope here.
}
