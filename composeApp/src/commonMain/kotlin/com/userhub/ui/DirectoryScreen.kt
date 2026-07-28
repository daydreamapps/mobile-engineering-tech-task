package com.userhub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.userhub.presentation.UserFeedUiState
import com.userhub.presentation.UserFeedViewModel
import com.userhub.presentation.UserUiModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DirectoryScreen(feedViewModel: UserFeedViewModel = koinViewModel()) {
    val state by feedViewModel.state.collectAsStateWithLifecycle()

    var selectedUser by remember { mutableStateOf<UserUiModel?>(null) }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                UserFeedUiState.Loading -> LoadingSkeleton()
                UserFeedUiState.NoInternet -> ConnectionErrorState(onRetry = feedViewModel::load)
                is UserFeedUiState.Content -> UserList(
                    users = current.users,
                    lastSyncLabel = current.lastSyncLabel,
                    onClick = { selectedUser = it },
                    onLongPress = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

}

@Composable
private fun UserList(
    users: List<UserUiModel>,
    lastSyncLabel: String?,
    onClick: (UserUiModel) -> Unit,
    onLongPress: (UserUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        if (lastSyncLabel != null) {
            item {
                Text(
                    text = lastSyncLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        items(users, key = { it.id }) { user ->
            DirectoryRow(
                user = user,
                onClick = { onClick(user) },
                onLongPress = { onLongPress(user) },
                modifier = Modifier.animateItem()
            )
        }
    }
}
