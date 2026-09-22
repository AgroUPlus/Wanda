package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.replay.ReplayEntry
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * The cards that are a ranked list: artists, the top song, genres, devices and the circle.
 *
 * All of them reuse `rememberShelfEntranceScale`, which is the app's existing staggered-reveal
 * primitive — a countdown that popped in with its own bespoke spring would be the one list in the
 * app that moved differently from every other.
 */

@Composable
internal fun ReplayTopArtistsCard(card: ReplayCard.TopArtists) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.primaryContainer,
        kicker = stringResource(R.string.replay_artists_kicker),
        headline = stringResource(R.string.replay_artists_headline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap)
        ) {
            card.artists.forEachIndexed { index, artist ->
                RankedRow(
                    rank = index + 1,
                    name = artist.name,
                    detail = pluralStringResource(
                        R.plurals.replay_play_count,
                        artist.value.toInt(),
                        artist.value
                    ),
                    index = index,
                    emphasised = index == 0
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.replay_artists_detail,
                    card.totalArtists,
                    card.totalArtists
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ReplayTopSongCard(card: ReplayCard.TopSong) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        kicker = stringResource(R.string.replay_song_kicker),
        headline = card.title
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RowGap)
        ) {
            Text(
                text = card.artist,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = pluralStringResource(
                    R.plurals.replay_song_detail,
                    card.plays.toInt(),
                    card.plays
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun ReplayGenresCard(card: ReplayCard.Genres) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.secondaryContainer,
        kicker = stringResource(R.string.replay_genres_kicker),
        headline = card.genres.first().name
    ) {
        // The first genre is the headline, so the list under it picks up at two.
        EntryList(entries = card.genres.drop(1), startRank = 2)
    }
}

/**
 * Where the year was actually listened to.
 *
 * Only ever drawn for more than one device: on an unpaired phone there is nothing to break down,
 * and a card reading "this phone, 100%" is not a fact about a fleet.
 */
@Composable
internal fun ReplayDevicesCard(card: ReplayCard.Devices) {
    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.secondaryContainer,
        kicker = stringResource(R.string.replay_devices_kicker),
        headline = pluralStringResource(
            R.plurals.replay_devices_headline,
            card.devices.size,
            card.devices.size
        )
    ) {
        EntryList(entries = card.devices, startRank = 1)
    }
}

/**
 * The circle's year: its anthem, whoever found it first, and the friend whose taste lines up best.
 *
 * Every line is optional because every part of it is: a circle with no shared anthem is a real
 * circle, and the card shows what it has rather than placeholders for what it does not.
 */
@Composable
internal fun ReplayCircleCard(card: ReplayCard.Circle) {
    val circle = card.circle

    ReplayCardFrame(
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        kicker = stringResource(R.string.replay_circle_kicker),
        headline = pluralStringResource(
            R.plurals.replay_circle_headline,
            circle.members.size,
            circle.members.size
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (circle.anthemTitle != null && circle.anthemArtist != null) {
                CircleLine(
                    label = stringResource(R.string.replay_circle_anthem),
                    value = stringResource(
                        R.string.replay_circle_anthem_value,
                        circle.anthemTitle,
                        circle.anthemArtist
                    )
                )
            }
            circle.trendsetter?.let { who ->
                CircleLine(
                    label = stringResource(R.string.replay_circle_trendsetter),
                    value = pluralStringResource(
                        R.plurals.replay_circle_trendsetter_value,
                        circle.trendsetterFirsts.toInt(),
                        who,
                        circle.trendsetterFirsts
                    )
                )
            }
            circle.closestFriend?.let { who ->
                CircleLine(
                    label = stringResource(R.string.replay_circle_closest),
                    value = stringResource(
                        R.string.replay_circle_closest_value,
                        who,
                        circle.closestScore
                    )
                )
            }
        }
    }
}

@Composable
private fun CircleLine(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EntryList(entries: List<ReplayEntry>, startRank: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RowGap)
    ) {
        entries.forEachIndexed { index, entry ->
            RankedRow(
                rank = index + startRank,
                name = entry.name,
                detail = pluralStringResource(
                    R.plurals.replay_play_count,
                    entry.value.toInt(),
                    entry.value
                ),
                index = index,
                emphasised = false
            )
        }
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    name: String,
    detail: String,
    index: Int,
    emphasised: Boolean
) {
    val scale = rememberShelfEntranceScale(index)

    Row(
        modifier = Modifier.fillMaxWidth().scale(scale),
        horizontalArrangement = Arrangement.spacedBy(RankGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = if (emphasised) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val RowGap = 12.dp
private val RankGap = 12.dp
