package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeSectionStyle
import com.wander.android.ui.screens.home.HorizontalTrackCard
import com.wander.android.ui.screens.home.SmartMixCard

private const val PreviewRows = 3
private const val PreviewCards = 8

private val RowStyles = setOf(HomeSectionStyle.TRACK_LIST, HomeSectionStyle.TRACK_PAGER)

/**
 * A shelf's real tracks, drawn small and inert for the customizer. Row layouts show a few rows and
 * every other layout shows a strip of cards: it is the shelf's content and rough shape, not a
 * pixel copy of each style, which is what makes ten shelves fit on one screen to be rearranged.
 */
@Composable
internal fun ShelfPreview(section: HomeSection, modifier: Modifier = Modifier) {
    when {
        section.mixes.isNotEmpty() -> PreviewStrip(modifier) {
            section.mixes.take(PreviewCards).forEach { mix -> SmartMixCard(mix = mix, onPlay = {}) }
        }

        section.style in RowStyles -> Column(modifier) {
            section.tracks.take(PreviewRows).forEach { track ->
                TrackRow(track = track, onPlay = {}, enabled = false)
            }
        }

        else -> PreviewStrip(modifier) {
            section.tracks.take(PreviewCards).forEachIndexed { index, track ->
                HorizontalTrackCard(track = track, index = index, onPlay = {}, enabled = false)
            }
        }
    }
}

/** A row that overflows its width without scrolling: the card is cut off, never draggable. */
@Composable
private fun PreviewStrip(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = 16.dp)
    ) { content() }
}
