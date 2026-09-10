package com.wander.android.ui.screens.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.TopSong
import com.wander.android.data.sources.agro.StatsPeriod
import com.wander.android.ui.components.ImmersiveHero

/**
 * The top of the statistics screen: the one song the window was mostly about, at full width.
 *
 * The screen used to open on a headline, a subtitle and a scrolling row of five small tiles, which
 * is a dashboard — you read it rather than recognise it. A listening history is about music, and
 * the cover of what you actually had on is the fastest way to say what the last week was.
 *
 * The fade is the same idea as `ArtistHero`: the picture melts into `surface` at its foot and the
 * text sits in the opaque part, so it stays legible whatever the cover is instead of relying on a
 * scrim tuned for one kind of artwork.
 *
 * [topSong] is null before anything has been played in the window, and then this is a plain
 * heading — an empty cover box with "Top song" written under it would be a promise the screen
 * cannot keep.
 */
@Composable
internal fun StatsHero(
    topSong: TopSong?,
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    topInset: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (topSong == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(topInset)
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
            ) {
                Text(text = "Listening", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Nothing played in this period yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            ImmersiveHero(
                imageUrl = topSong.artworkUrl,
                contentDescription = topSong.title,
                aspect = CoverAspect,
                captionAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Top song",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = topSong.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (topSong.artist.isNotBlank()) {
                            Text(
                                text = topSong.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    topSong.seconds?.let { seconds ->
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                text = "Listened time",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = formatListeningTime(seconds),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        PeriodPicker(
            period = period,
            onPeriod = onPeriod,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(topInset)
                .padding(end = 16.dp, top = 8.dp)
        )
    }
}

/**
 * Which stretch of time the screen is about.
 *
 * A menu rather than the row of toggles this used to be: four periods in a scrolling button group
 * cost a full row of the screen to say one word, and the row scrolled horizontally *inside* a
 * vertically scrolling list, which is a gesture conflict for no gain.
 */
@Composable
private fun PeriodPicker(
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { open = true },
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(text = period.label, style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StatsPeriod.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        open = false
                        onPeriod(entry)
                    }
                )
            }
        }
    }
}

/** Reads as a cover with room for two lines of title under it, not as a square with text on it. */
private const val CoverAspect = 0.92f

/** Shared by the hero's caption row and the quick-fact tiles. */
internal val StatsGutter = 20.dp
