package com.wander.android.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.headerInset

/**
 * Songs friends handed you, and the ones you handed out, presented as a conversation feed.
 *
 * Drops are messages centered around music — styled as conversational speech bubbles with
 * rich attached playable track cards.
 *
 * The open conversation is *state*, not a destination, which is why back is handled twice over:
 * the button in the bar closes the thread first, and so does the system gesture. Without the
 * second one the two disagreed — the gesture left the inbox entirely from inside a conversation,
 * which is not what going back from a conversation means anywhere else.
 */
@Composable
internal fun InboxScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = state.openWith != null) { viewModel.closeThread() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(contentPadding.headerInset())
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    // Back closes the open thread first. Leaving the screen from inside one
                    // would make the list unreachable without opening the inbox again.
                    onClick = { if (state.openWith != null) viewModel.closeThread() else onBack() }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = state.openWith?.let { state.nameOf(it) } ?: "Messages",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                if (state.unread > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = if (state.unread > 99) "99+" else "${state.unread} new",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            val openWith = state.openWith
            when {
                openWith != null -> InboxConversation(
                    state = state,
                    contentPadding = contentPadding,
                    onPlay = viewModel::play,
                    onReact = viewModel::react,
                    onRemove = viewModel::remove
                )

                state.loading && state.threads.isEmpty() ->
                    InboxThreadListSkeleton(contentPadding = contentPadding)

                state.threads.isEmpty() -> EmptyState(
                    title = "No conversations yet",
                    message = "Press and hold any track to send it to a friend with a note."
                )

                else -> InboxThreadList(
                    state = state,
                    contentPadding = contentPadding,
                    onOpenThread = viewModel::openThread
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
