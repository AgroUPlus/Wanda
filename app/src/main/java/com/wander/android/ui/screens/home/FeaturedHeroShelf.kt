package com.wander.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.isPlayableNow
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.rememberShelfArtworkShape
import com.wander.android.ui.components.rememberShelfEntranceScale
import com.wander.android.ui.components.scrollingTitle

/**
 * One oversized hero card, and a short strip of related tracks beside it.
 *
 * The counterweight to [LargeCardShelf]'s "everything is equally big": this shelf is for the
 * sections that are genuinely built around one thing — "Because you listened to X" — where the
 * lead track deserves the emphasis and the rest reads as *because of it* rather than as five more
 * equal cards.
 */
@Composable
internal fun FeaturedHeroShelf(
    tracks: List<UnifiedTrack>,
    sectionId: String,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) return
    val hero = tracks.first()
    val strip = tracks.drop(1).take(StripSize)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .height(HeroHeight)
            .padding(horizontal = 20.dp)
    ) {
        HeroCard(
            track = hero,
            onPlay = { onPlay(0) },
            onLongPress = { onLongPress(hero) }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            strip.forEachIndexed { stripIndex, track ->
                StripRow(
                    track = track,
                    onPlay = { onPlay(stripIndex + 1) },
                    onLongPress = { onLongPress(track) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean = track.isPlayableNow()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by rememberPressScale(interactionSource, label = "heroCardPress")
    val entranceScale = rememberShelfEntranceScale(0)
    val artworkShape = rememberShelfArtworkShape(isPressed)

    Column(
        modifier = Modifier
            .width(HeroWidth)
            .scale(scale * entranceScale)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = track.title,
            sizeDp = HeroWidth,
            shape = artworkShape,
            modifier = Modifier.size(HeroWidth)
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 8.dp).scrollingTitle()
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
        )
    }
}

/** A single line of the strip beside the hero: small artwork, title and artist, nothing else. */
@Composable
private fun StripRow(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    enabled: Boolean = track.isPlayableNow()
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .combinedClickable(
                onClick = { if (enabled) onPlay() },
                onLongClick = onLongPress
            )
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            sizeDp = StripArtworkSize,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(StripArtworkSize)
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
    }
}

private const val StripSize = 4

/** Material's disabled-content opacity, matching `TrackRow`. */
private const val DisabledAlpha = 0.38f

private val HeroWidth = 168.dp
private val HeroHeight = 236.dp
private val StripArtworkSize = 44.dp
