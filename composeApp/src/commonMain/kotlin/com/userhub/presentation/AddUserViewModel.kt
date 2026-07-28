package com.userhub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.userhub.data.remote.UserDto
import com.userhub.domain.usecase.AddUserUseCase
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddUserState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class AddUserViewModel(private val addUser: AddUserUseCase) : ViewModel() {

    private val _state = MutableStateFlow(AddUserState())
    val state: StateFlow<AddUserState> = _state.asStateFlow()

    fun submit(name: String, email: String, onCreated: (UserDto) -> Unit) {
        viewModelScope.launch {
            _state.value = AddUserState(isSubmitting = true)
            try {
                val response = addUser(name, email)
                if (response.status == HttpStatusCode.Created) {
                    _state.value = AddUserState()
                    onCreated(response.body())
                } else {
                    _state.value = AddUserState(
                        errorMessage = "No internet connection. Please check your network and try again."
                    )
                }
            } catch (e: Exception) {
                _state.value = AddUserState(
                    errorMessage = "No internet connection. Please check your network and try again."
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
