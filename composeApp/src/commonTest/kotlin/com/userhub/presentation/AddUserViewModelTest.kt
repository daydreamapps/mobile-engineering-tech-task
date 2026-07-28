package com.userhub.presentation

import com.userhub.data.remote.UserDto
import com.userhub.domain.usecase.AddUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddUserViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit sends the entered values to the repository`() = runTest {
        val repository = FakeUserRepository()
        val viewModel = AddUserViewModel(AddUserUseCase(repository))

        viewModel.submit("Grace Hopper", "grace@example.com") {}
        withContext(Dispatchers.Default) { delay(100) }

        assertEquals("Grace Hopper", repository.createdName)
        assertEquals("grace@example.com", repository.createdEmail)
    }

    @Test
    fun `successful creation notifies the caller and clears the error`() = runTest {
        val repository = FakeUserRepository()
        val viewModel = AddUserViewModel(AddUserUseCase(repository))
        var created: UserDto? = null

        viewModel.submit("Grace Hopper", "grace@example.com") { created = it }
        withContext(Dispatchers.Default) { delay(100) }

        assertEquals("grace@example.com", created?.email)
        assertNull(viewModel.state.value.errorMessage)
    }
}
