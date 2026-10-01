package com.wander.android.ui.screens.player

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wander.android.R
import com.wander.android.core.database.entity.EpisodeExtrasEntity
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.data.podcast.Chapter
import com.wander.android.data.podcast.TranscriptCue
import com.wander.android.ui.components.WandaSheet

internal enum class EpisodePanel { CHAPTERS, TRANSCRIPT }

/** The chapter list and the transcript of the playing episode, each a sheet that seeks on tap. */
@Composable
internal fun EpisodePanels(
    panel: EpisodePanel?,
    extras: EpisodeExtrasEntity?,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    viewModel: EpisodeExtrasViewModel = hiltViewModel()
) {
    if (extras == null) return
    when (panel) {
        EpisodePanel.CHAPTERS -> extras.chaptersUrl?.let { url ->
            ChaptersSheet(url, playerConnection, viewModel, onDismiss)
        }
        EpisodePanel.TRANSCRIPT -> extras.transcriptUrl?.let { url ->
            TranscriptSheet(url, extras.transcriptMime, playerConnection, viewModel, onDismiss)
        }
        null -> Unit
    }
}

@Composable
private fun ChaptersSheet(
    url: String,
    playerConnection: PlayerConnection,
    viewModel: EpisodeExtrasViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<List<Chapter>>?>(initialValue = null, url) { value = viewModel.chapters(url) }
    val position = rememberPlaybackPosition(playerConnection)
    WandaSheet(onDismissRequest = onDismiss) { animatedDismiss ->
        PanelTitle(R.string.chapters_title)
        PanelBody(result, R.string.chapters_empty, R.string.chapters_load_failed) { chapters ->
            val active by remember(chapters) {
                derivedStateOf { chapters.indexOfLast { it.startMs <= position.value.positionMs } }
            }
            val listState = rememberLazyListState()
            LaunchedEffect(chapters) { listState.scrollToItem(active.coerceAtLeast(0)) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).navigationBarsPadding()) {
                itemsIndexed(chapters, key = { index, chapter -> "$index-${chapter.startMs}" }) { index, chapter ->
                    PanelRow(
                        label = formatTime(chapter.startMs),
                        text = chapter.title,
                        active = index == active,
                        onClick = { playerConnection.seekTo(chapter.startMs); animatedDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptSheet(
    url: String,
    mime: String?,
    playerConnection: PlayerConnection,
    viewModel: EpisodeExtrasViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<List<TranscriptCue>>?>(initialValue = null, url) {
        value = viewModel.transcript(url, mime)
    }
    val position = rememberPlaybackPosition(playerConnection)
    WandaSheet(onDismissRequest = onDismiss) {
        PanelTitle(R.string.transcript_title)
        PanelBody(result, R.string.transcript_empty, R.string.transcript_load_failed) { cues ->
            val active by remember(cues) {
                derivedStateOf { cues.indexOfLast { it.startMs <= position.value.positionMs } }
            }
            val listState = rememberLazyListState()
            // Follows the speaker, but never fights a finger: a listener reading ahead or back keeps their place.
            LaunchedEffect(active) {
                if (active >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem(active)
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).navigationBarsPadding()) {
                itemsIndexed(cues, key = { index, cue -> "$index-${cue.startMs}" }) { index, cue ->
                    PanelRow(
                        label = formatTime(cue.startMs),
                        text = cue.text,
                        active = index == active,
                        onClick = { playerConnection.seekTo(cue.startMs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

/** Loading, failure and empty states shared by both panels; [content] runs only with a non-empty list. */
@Composable
private fun <T> PanelBody(
    result: Result<List<T>>?,
    @StringRes emptyMessage: Int,
    @StringRes failureMessage: Int,
    content: @Composable (List<T>) -> Unit
) {
    val items = result?.getOrNull()
    when {
        result == null -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        result.isFailure -> PanelMessage(stringResource(failureMessage, result.exceptionOrNull()?.message.orEmpty()))
        items.isNullOrEmpty() -> PanelMessage(stringResource(emptyMessage))
        else -> content(items)
    }
}

@Composable
private fun PanelMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
    )
}

@Composable
private fun PanelRow(label: String, text: String, active: Boolean, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
