package com.userhub.presentation

sealed interface UserFeedUiState {
    data object Loading : UserFeedUiState
    data class Content(
        val users: List<UserUiModel>,
        val lastSyncLabel: String? = null
    ) : UserFeedUiState
    data object NoInternet : UserFeedUiState
}
