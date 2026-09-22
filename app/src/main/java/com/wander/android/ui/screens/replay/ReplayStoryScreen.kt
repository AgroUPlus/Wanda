package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R

/**
 * Agro Replay: a year's listening, one card at a time.
 *
 * The screen itself only decides *which* of three things is on display — loading, a failure, or the
 * story — and hands each card to the composable that draws it. Everything about how the story moves
 * lives in [ReplayStoryScaffold].
 */
@Composable
internal fun ReplayStoryScreen(
    year: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReplayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(year) { viewModel.load(year) }

    when {
        state.isLoading -> Centred(modifier) { CircularProgressIndicator() }

        state.failure != null -> Centred(modifier) {
            Text(
                text = state.failure.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(FailureGutter)
            )
        }

        else -> ReplayStoryScaffold(
            deck = state.deck,
            onDismiss = onDismiss,
            modifier = modifier
        ) { card ->
            ReplayCardContent(
                card = card,
                state = state,
                onSave = viewModel::save,
                onSaveAndPurge = viewModel::saveAndPurge,
                onDone = onDismiss
            )
        }
    }
}

/**
 * One card to one composable.
 *
 * Exhaustive by construction — a `when` over a sealed interface with no `else`, so adding a card to
 * [ReplayCard] without drawing it will not compile rather than showing a blank page.
 */
@Composable
private fun ReplayCardContent(
    card: ReplayCard,
    state: ReplayUiState,
    onSave: () -> Unit,
    onSaveAndPurge: () -> Unit,
    onDone: () -> Unit
) {
    when (card) {
        is ReplayCard.Intro -> ReplayIntroCard(card)
        is ReplayCard.Minutes -> ReplayMinutesCard(card)
        is ReplayCard.Shape -> ReplayShapeCard(card)
        is ReplayCard.Hours -> ReplayHoursCard(card)
        is ReplayCard.TopArtists -> ReplayTopArtistsCard(card)
        is ReplayCard.TopSong -> ReplayTopSongCard(card)
        is ReplayCard.Genres -> ReplayGenresCard(card)
        is ReplayCard.Discovery -> ReplayDiscoveryCard(card)
        is ReplayCard.Streak -> ReplayStreakCard(card)
        is ReplayCard.Devices -> ReplayDevicesCard(card)
        is ReplayCard.Charts -> ReplayChartsCard(card)
        is ReplayCard.Circle -> ReplayCircleCard(card)
        is ReplayCard.Silence -> ReplaySilenceCard(card)
        is ReplayCard.Outro -> ReplayOutroCard(
            card = card,
            state = state,
            onSave = onSave,
            onSaveAndPurge = onSaveAndPurge,
            onDone = onDone
        )
    }
}

@Composable
private fun ReplayIntroCard(card: ReplayCard.Intro) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.primaryContainer,
        kicker = stringResource(R.string.replay_intro_kicker),
        headline = stringResource(R.string.replay_intro_headline, card.year)
    ) {
        Text(
            // Said on the first card rather than buried: a recap from one phone and a recap from a
            // whole account are different claims, and the reader should know which this is.
            text = stringResource(
                if (card.isFleetWide) {
                    R.string.replay_intro_scope_fleet
                } else {
                    R.string.replay_intro_scope_device
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A year with nothing in it. One honest card instead of eleven empty ones. */
@Composable
private fun ReplaySilenceCard(card: ReplayCard.Silence) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.surfaceVariant,
        kicker = stringResource(R.string.replay_intro_kicker),
        headline = stringResource(R.string.replay_silence_headline, card.year)
    ) {
        Text(
            text = stringResource(R.string.replay_silence_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Centred(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val FailureGutter = 32.dp
