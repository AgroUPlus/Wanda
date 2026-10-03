package com.wander.android.ui.screens.social

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamNowPlaying
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.heroTitle
import kotlinx.coroutines.delay

/**
 * What the room is hearing, as the jam's hero: the cover, the title at hero size, a wavy bar
 * ticking along with server time, and the one thing anybody in the room can do about it — vote to
 * skip.
 */
@Composable
internal fun JamNowPlayingCard(
    jam: Jam,
    unresolvable: String?,
    outOfSync: Boolean,
    viewModel: JamViewModel
) {
    val now = jam.nowPlaying
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 40.dp, y = 70.dp)
                    .size(190.dp)
                    .clip(PersonShape)
                    .background(MaterialTheme.extraColors.primaryShapeAccent)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text(
                    text = stringResource(if (now == null) R.string.jam_between_tracks else R.string.jam_everyone_is_hearing),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                )
                if (now == null) {
                    Text(
                        text = stringResource(R.string.social_nothing_playing_yet),
                        style = MaterialTheme.typography.heroTitle.copy(letterSpacing = (-0.5).sp)
                    )
                } else {
                    NowPlayingDetails(now, unresolvable, outOfSync, viewModel)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingDetails(
    now: JamNowPlaying,
    unresolvable: String?,
    outOfSync: Boolean,
    viewModel: JamViewModel
) {
    TrackHeader(now)
    RoomProgress(now)
    SkipVote(now, viewModel::voteSkip)
    AnimatedVisibility(visible = outOfSync && unresolvable == null) {
        DriftedFromRoom(viewModel::resync)
    }
    AnimatedVisibility(visible = unresolvable != null) {
        Text(
            text = stringResource(R.string.jam_unresolvable, unresolvable.orEmpty()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TrackHeader(now: JamNowPlaying) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            url = now.artworkUrl,
            contentDescription = null,
            sizeDp = 104.dp,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(104.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = now.title,
                style = MaterialTheme.typography.heroTitle.copy(letterSpacing = (-0.5).sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = now.artist,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoomProgress(now: JamNowPlaying) {
    val progress by rememberRoomProgress(now)
    val stroke = with(LocalDensity.current) { Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round) }
    LinearWavyProgressIndicator(
        progress = { progress },
        color = MaterialTheme.colorScheme.onPrimary,
        trackColor = MaterialTheme.extraColors.onPrimaryTrack,
        stroke = stroke,
        trackStroke = stroke,
        gapSize = 10.dp,
        stopSize = 4.dp,
        wavelength = 30.dp,
        modifier = Modifier.fillMaxWidth().height(16.dp)
    )
}

/** Progress through the room's current track, ticking locally from the server-reported position. */
@Composable
private fun rememberRoomProgress(now: JamNowPlaying): State<Float> =
    produceState(0f, now.trackId, now.positionMs, now.durationMs) {
        if (now.durationMs <= 0L) {
            value = 0f
            return@produceState
        }
        val base = now.positionMs
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            value = ((base + elapsed).toFloat() / now.durationMs).coerceIn(0f, 1f)
            if (value >= 1f) break
            delay(500)
        }
    }
