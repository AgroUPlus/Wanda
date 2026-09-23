package com.wander.android.ui.screens.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.replay.ReplayEntry
import com.wander.android.ui.components.rememberShelfEntranceScale

/**
 * The cards that are a plain ranked list: genres and devices, plus the rows the picture cards
 * in `ReplayArtCards` share.
 *
 * All of them reuse `rememberShelfEntranceScale`, which is the app's existing staggered-reveal
 * primitive — a countdown that popped in with its own bespoke spring would be the one list in the
 * app that moved differently from every other.
 */

@Composable
internal fun ReplayGenresCard(card: ReplayCard.Genres) {
    ReplayCardFrame(
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

@Composable
internal fun CircleLine(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = replayMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = LocalContentColor.current,
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

/**
 * One line of a ranking: a bold rank chip, an optional picture, the name and its count. Scales in
 * on the shared stagger.
 */
@Composable
internal fun RankedRow(
    rank: Int,
    name: String,
    detail: String,
    index: Int,
    emphasised: Boolean,
    leading: (@Composable () -> Unit)? = null
) {
    val scale = rememberShelfEntranceScale(index)

    Row(
        modifier = Modifier.fillMaxWidth().scale(scale),
        horizontalArrangement = Arrangement.spacedBy(RankGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(RankChip)
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = RankChipAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Black
            )
        }
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = if (emphasised) {
                    MaterialTheme.typography.headlineSmallEmphasized
                } else {
                    MaterialTheme.typography.titleMediumEmphasized
                },
                color = LocalContentColor.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = replayMuted
            )
        }
    }
}

internal val RowGap = 12.dp
private val RankGap = 12.dp
private val RankChip = 32.dp
private const val RankChipAlpha = 0.16f
