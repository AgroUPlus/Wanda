package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.WandaNoticeHost
import com.wander.android.ui.components.BackLayers
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.rememberLastLive
import com.wander.android.ui.components.headerInset

/**
 * Songs friends handed you, and the ones you handed out, presented as a conversation feed.
 *
 * Drops are messages centered around music — styled as conversational speech bubbles with
 * rich attached playable track cards.
 *
 * The open conversation is *state*, not a destination, which is why back is handled smoothly:
 * tapping back or swiping back with predictive gesture closes the thread with expressive motion.
 */
@Composable
internal fun InboxScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    /**
     * Whose conversation to land in, when the caller already knows.
     *
     * Opening a shared song from Activity means one particular exchange; going by way of a list of
     * every exchange, to pick the one just tapped, is a step that exists only because this screen
     * used to be the only way in.
     */
    openWith: String? = null,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the argument, so returning to a list this screen was opened *into* does not
    // immediately reopen the thread the back press just left.
    LaunchedEffect(openWith) {
        if (!openWith.isNullOrBlank()) viewModel.openThread(openWith)
    }
    // Only while the thread is open *and* the screen resumed: another screen on top, or the app in
    // the background, means the user is no longer looking at what that friend sends.
    state.openWith?.let { username ->
        LifecycleResumeEffect(username) {
            viewModel.onThreadShown(username)
            onPauseOrDispose { viewModel.onThreadHidden(username) }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription =
                            stringResource(R.string.common_back)
                    )
                }
                Text(
                    text = state.openWith?.let { state.nameOf(it) } ?: "Messages",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                // Only inside a thread, and only one that is sealed throughout. A padlock over a
                // conversation whose older half is in the clear would be a claim this app cannot
                // make — the boundary line inside says it accurately instead.
                if (state.openWith != null && isFullySealed(state.conversation)) {
                    EncryptedThreadLock(modifier = Modifier.padding(end = 8.dp))
                }
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

            // The thread opens over the list, which stays composed underneath: a back swipe shrinks
            // the thread into a card and shows the list coming forward behind it.
            BackLayers(
                page = state.openWith,
                onBack = viewModel::closeThread,
                base = {
                    when {
                        state.loading && state.threads.isEmpty() ->
                            InboxThreadListSkeleton(contentPadding = contentPadding)

                        state.threads.isEmpty() -> EmptyState(
                            title = R.string.social_no_conversations_yet,
                            message = R.string.social_press_hold_any_track_send
                        )

                        else -> InboxThreadList(
                            state = state,
                            contentPadding = contentPadding,
                            onOpenThread = viewModel::openThread
                        )
                    }
                }
            ) { username ->
                // Closing clears the conversation at once; the leaving card keeps what it showed.
                val shown = rememberLastLive(state, live = state.openWith == username)
                InboxConversation(
                    state = shown,
                    contentPadding = contentPadding,
                    onPlay = viewModel::play,
                    onReact = viewModel::react,
                    onRemove = viewModel::remove
                )
            }
        }

        WandaNoticeHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )
    }
}
