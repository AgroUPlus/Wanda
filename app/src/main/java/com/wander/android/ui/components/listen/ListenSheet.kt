package com.wander.android.ui.components.listen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.wander.android.data.repository.RecognitionCandidate
import com.wander.android.data.repository.RecognitionProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.repository.Recognition
import com.wander.android.data.repository.RecognitionEngine
import com.wander.android.ui.components.Artwork

/**
 * "What is this?" — the microphone, matched against the user's own library.
 *
 * A sheet rather than a screen. Listening takes seconds and ends in one fact; a destination would
 * make the user navigate back out of an answer they have already read.
 *
 * What it will never do is guess. If the record is not in the library there is no answer to give,
 * and the sheet says so plainly along with how much of the library it can currently see — which is
 * the difference between a feature that looks broken and one whose limits are legible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenSheet(
    onDismiss: () -> Unit,
    onOpenTrack: () -> Unit,
    viewModel: ListenViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val indexed by viewModel.indexedTracks.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    // Starts on open and stops on close — including a swipe-away, which is why this is a
    // DisposableEffect and not a click handler. Leaving the microphone running behind a dismissed
    // sheet is the one failure mode a feature like this must not have.
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            AnimatedContent(
                targetState = state,
                // Keyed on the kind of state, not the value. `Narrowing` carries a new ranking
                // every second, and without this the whole panel would cross-fade on each one —
                // the list would flicker instead of reordering, which is the opposite of the
                // thing being shown.
                contentKey = { it.kind },
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.92f))
                },
                label = "listenState",
                contentAlignment = Alignment.Center
            ) { current ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (current) {
                        ListenState.Idle, ListenState.Listening -> Listening(indexed)
                        is ListenState.Narrowing -> Narrowing(current.progress)
                        is ListenState.Matched -> Matched(
                            recognition = current.recognition,
                            onPlay = {
                                viewModel.playMatch()
                                onOpenTrack()
                            }
                        )
                        ListenState.NoMatch -> NoMatch(indexed, onRetry = viewModel::start)
                        ListenState.Failed -> Failed(onRetry = viewModel::start)
                    }
                }
            }
        }
    }
}

@Composable
private fun Listening(indexedTracks: Int) {
    PulsingMic()
    Text("Listening…", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = if (indexedTracks > 0) {
            "Play it, or hum it. Matching against $indexedTracks " +
                "${if (indexedTracks == 1) "track" else "tracks"} stored on this device."
        } else {
            "Nothing is indexed yet. Recognition works on music saved to this device, and the " +
                "index is built while charging."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * The shortlist, while the microphone is still open.
 *
 * Every row here is a track the clip genuinely aligns with, and the order is the matcher's real
 * ranking at this instant — recomputed each second against a second more audio. A leader changing
 * hands is a true thing about the clip, not an effect: it means the newly arrived audio aligned
 * better somewhere else.
 *
 * The bar is drawn from the *lead* over the noise floor rather than from the raw vote count.
 * Votes carry a floor of coincidences that grows with the size of the index, so bars drawn from
 * them would all sit nearly full — including the wrong ones. The lead is the quantity the verdict
 * is actually taken on, so it is the one worth showing.
 */
@Composable
private fun Narrowing(progress: RecognitionProgress) {
    PulsingMic()
    Text("Narrowing it down…", style = MaterialTheme.typography.headlineSmall)

    val strongest = progress.candidates.maxOfOrNull { it.lead }?.coerceAtLeast(1) ?: 1
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
    ) {
        items(progress.candidates, key = { it.trackId }) { candidate ->
            CandidateRow(
                candidate = candidate,
                // Relative to the current leader: the absolute numbers climb throughout the
                // capture, and a bar scaled to them would creep upward for everyone at once and
                // say nothing about who is ahead.
                strength = candidate.lead.toFloat() / strongest,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: RecognitionCandidate,
    strength: Float,
    modifier: Modifier = Modifier
) {
    // Animated so a row that gains ground grows into it rather than jumping, which is what makes
    // the ranking legible while it is still moving.
    val width by animateFloatAsState(
        targetValue = strength.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "candidateStrength"
    )
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = candidate.artworkUrl,
                contentDescription = null,
                sizeDp = 36.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun Matched(recognition: Recognition, onPlay: () -> Unit) {
    Artwork(
        url = recognition.track.artworkUrl,
        contentDescription = recognition.track.title,
        sizeDp = 140.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.size(140.dp)
    )
    Text(
        text = recognition.track.title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = recognition.track.artist,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (recognition.engine == RecognitionEngine.MELODY) {
        // Said out loud, because the two engines are not equally sure. A landmark match heard the
        // record; this one matched the shape of a tune somebody hummed, and a listener shown a
        // confident wrong answer has no way to know which kind they were given.
        Text(
            text = "Matched by melody — this is a guess from the tune, not the recording.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) {
        // Says where it will start, because it is not the beginning — picking the song up where
        // the room has reached is the point, and a plain "Play" would look like a bug. A hummed
        // match has no position to resume from, so it simply plays.
        if (recognition.engine == RecognitionEngine.MELODY) Text("Play")
        else Text("Play from ${formatPosition(recognition.positionSeconds)}")
    }
}

@Composable
private fun NoMatch(indexedTracks: Int, onRetry: () -> Unit) {
    Text("No match", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = if (indexedTracks > 0) {
            "That is not one of the $indexedTracks tracks on this device — or the room was too " +
                "loud to hear it clearly. Humming works too, if you know how it goes."
        } else {
            "Nothing is indexed yet, so there was nothing to match against. The index is built " +
                "in the background while this phone is charging."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Listen again") }
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Icon(
        imageVector = Icons.Rounded.MicOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(56.dp)
    )
    Text("Could not listen", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Another app may be using the microphone.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Try again") }
}

private fun formatPosition(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
