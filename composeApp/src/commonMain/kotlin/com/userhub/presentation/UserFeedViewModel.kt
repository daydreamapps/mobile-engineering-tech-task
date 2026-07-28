package com.userhub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.userhub.data.remote.UserDto
import com.userhub.data.repository.UsersResult
import com.userhub.domain.time.TimeProvider
import com.userhub.domain.time.formatRelativeTime
import com.userhub.domain.usecase.DeleteUserUseCase
import com.userhub.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserFeedViewModel(
    private val getUsers: GetUsersUseCase,
    private val deleteUser: DeleteUserUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<UserFeedUiState>(UserFeedUiState.Loading)
    val state: StateFlow<UserFeedUiState> = _state.asStateFlow()

    private var pendingUndo: Pair<Int, UserUiModel>? = null

    init {
        load()
    }

    fun load() {
        _state.value = UserFeedUiState.Loading
        viewModelScope.launch {
            when (val result = getUsers()) {
                is UsersResult.Success -> {
                    val lastSyncLabel = result.lastSyncMillis?.let {
                        "Offline — last updated ${formatRelativeTime(it, TimeProvider.nowEpochMillis())}"
                    }
                    _state.value = UserFeedUiState.Content(result.users.toUiModels(), lastSyncLabel)
                }

                UsersResult.NoInternet -> _state.value = UserFeedUiState.NoInternet
            }
        }
    }

    fun onUserCreated(user: UserDto) {
        val content = _state.value as? UserFeedUiState.Content ?: return
        _state.value = content.copy(users = listOf(user.toNewUiModel()) + content.users)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onDeleteConfirmed(user: UserUiModel) {
        val content = _state.value as? UserFeedUiState.Content ?: return
        val index = content.users.indexOf(user)
        if (index < 0) return
        pendingUndo = index to user
        _state.value = content.copy(users = content.users - user)
        // run outside the screen scope so the delete always completes
        GlobalScope.launch {
            runCatching { deleteUser(user.id) }
        }
    }

    fun onUndo() {
        val (index, user) = pendingUndo ?: return
        val content = _state.value as? UserFeedUiState.Content ?: return
        val users = content.users.toMutableList()
        users.add(index.coerceAtMost(users.size), user)
        _state.value = content.copy(users = users)
        pendingUndo = null
    }
}
