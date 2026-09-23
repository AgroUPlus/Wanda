package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.wander.android.R
import com.wander.android.data.replay.ReplayArtwork
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * The cards with faces and covers on them: top artists, the song of the year, discoveries and the
 * circle.
 *
 * Every picture is optional. [ReplayArtwork] only knows what this device has seen, and a name it
 * cannot picture is drawn as its initial on a shape rather than a stock placeholder.
 */

@Composable
internal fun ReplayTopArtistsCard(card: ReplayCard.TopArtists, artwork: ReplayArtwork) {
    val top = card.artists.first()

    ReplayCardFrame(
        kicker = stringResource(R.string.replay_artists_kicker),
        headline = stringResource(R.string.replay_artists_headline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Number one gets the poster treatment; everyone else is a row.
            ReplayShapedArt(
                url = artwork.artist(top.name),
                name = top.name,
                shape = MaterialShapes.Cookie9Sided,
                size = TopArtistArt,
                modifier = Modifier.scale(rememberShelfEntranceScale(0))
            )
            Text(
                text = top.name,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            card.artists.drop(1).forEachIndexed { offset, artist ->
                RankedRow(
                    rank = offset + 2,
                    name = artist.name,
                    detail = pluralStringResource(
                        R.plurals.replay_play_count,
                        artist.value.toInt(),
                        artist.value
                    ),
                    index = offset + 1,
                    emphasised = false,
                    leading = {
                        ReplayShapedArt(
                            url = artwork.artist(artist.name),
                            name = artist.name,
                            shape = MaterialShapes.Circle,
                            size = ReplayAvatar
                        )
                    }
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.replay_artists_detail,
                    card.totalArtists,
                    card.totalArtists
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = replayMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The cover turns on its scalloped edge like a record on a platter. */
@Composable
internal fun ReplayTopSongCard(card: ReplayCard.TopSong, artwork: ReplayArtwork) {
    ReplayCardFrame(
        kicker = stringResource(R.string.replay_song_kicker),
        headline = card.title
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RowGap)
        ) {
            ReplayShapedArt(
                url = artwork.track(card.title, card.artist),
                name = card.title,
                shape = MaterialShapes.Cookie12Sided,
                size = ReplayHeroArt,
                spinning = true,
                modifier = Modifier.scale(rememberShelfEntranceScale(0))
            )
            Text(
                text = card.artist,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center
            )
            Text(
                text = pluralStringResource(
                    R.plurals.replay_song_detail,
                    card.plays.toInt(),
                    card.plays
                ),
                style = MaterialTheme.typography.titleMedium,
                color = replayMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Up to three new faces, tumbled together at jaunty angles, with their names underneath. */
@Composable
internal fun ReplayDiscoveryCard(card: ReplayCard.Discovery, artwork: ReplayArtwork) {
    ReplayCardFrame(
        kicker = stringResource(R.string.replay_discovery_kicker),
        headline = pluralStringResource(
            R.plurals.replay_discovery_headline,
            card.newArtists,
            card.newArtists
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RowGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                card.names.forEachIndexed { index, name ->
                    val slot = DiscoverySlots[index % DiscoverySlots.size]
                    ReplayShapedArt(
                        url = artwork.artist(name),
                        name = name,
                        shape = slot.shape,
                        size = DiscoveryArt,
                        modifier = Modifier
                            .offset(x = DiscoveryOverlap * ((card.names.size - 1) / 2f - index), y = slot.lift)
                            .rotate(slot.tilt)
                            .scale(rememberShelfEntranceScale(index))
                    )
                }
            }
            card.names.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The circle's year: its anthem, whoever found it first, and the friend whose taste lines up best.
 *
 * Every line is optional because every part of it is: a circle with no shared anthem is a real
 * circle, and the card shows what it has rather than placeholders for what it does not.
 */
@Composable
internal fun ReplayCircleCard(card: ReplayCard.Circle, artwork: ReplayArtwork) {
    val circle = card.circle

    ReplayCardFrame(
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
                ReplayShapedArt(
                    url = artwork.track(circle.anthemTitle, circle.anthemArtist),
                    name = circle.anthemTitle,
                    shape = MaterialShapes.Clover4Leaf,
                    size = AnthemArt,
                    spinning = true
                )
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

/** Where each discovered face sits in the tumble: its shape, tilt and how far it hops up. */
private class DiscoverySlot(
    val shape: RoundedPolygon,
    val tilt: Float,
    val lift: Dp
)

private val DiscoverySlots = listOf(
    DiscoverySlot(MaterialShapes.Clover4Leaf, tilt = -12f, lift = 8.dp),
    DiscoverySlot(MaterialShapes.Sunny, tilt = 6f, lift = (-10).dp),
    DiscoverySlot(MaterialShapes.Gem, tilt = 14f, lift = 6.dp)
)

private val TopArtistArt = 136.dp
private val DiscoveryArt = 112.dp
private val DiscoveryOverlap = 16.dp
private val AnthemArt = 120.dp
