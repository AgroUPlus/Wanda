package com.wander.android.ui.screens.social

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.headerInset
import kotlin.coroutines.cancellation.CancellationException

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
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = state.openWith != null) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            viewModel.closeThread()
        } catch (e: CancellationException) {
            backProgress = 0f
        } finally {
            backProgress = 0f
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

            AnimatedContent(
                targetState = state.openWith,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width / 6 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 6 } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width / 3 } + fadeOut())
                    }
                },
                label = "inbox_thread_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (state.openWith != null && backProgress > 0f) {
                            val scale = 1f - (backProgress * 0.08f)
                            scaleX = scale
                            scaleY = scale
                            translationX = backProgress * 120f
                            alpha = 1f - (backProgress * 0.3f)
                        }
                    }
            ) { openWith ->
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
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
