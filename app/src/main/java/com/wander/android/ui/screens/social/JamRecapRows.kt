package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.JamRecap
import com.wander.android.ui.components.SunnyShape

/** How many songs show before "Show all". Enough to jog a memory, not enough to be the list. */
private const val PREVIEW_TRACKS = 3

/**
 * The three things worth remembering about the room. Each is left out when the room never voted
 * on it — "most loved" with no approvals would be a coin toss presented as a fact.
 */
@Composable
internal fun RecapHighlights(recap: JamRecap) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        recap.topContributor?.let { top ->
            HighlightTile(
                label = stringResource(R.string.jam_recap_top_dj),
                value = pluralStringResource(R.plurals.jam_recap_top_dj_value, top.tracks.toInt(), top.tracks.toInt(), top.username),
                emblem = { Text(top.tracks.toString(), style = MaterialTheme.typography.titleMedium, color = colors.onTertiary) },
                emblemColor = colors.tertiary,
                emblemShape = SunnyShape
            )
        }
        recap.mostLoved?.let { loved ->
            HighlightTile(
                label = stringResource(R.string.jam_recap_most_loved),
                value = "${loved.title} · ${loved.artist}",
                emblem = { Icon(Icons.Rounded.Favorite, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp)) },
                emblemColor = colors.primary,
                emblemShape = CircleShape
            )
        }
        recap.mostSkipped?.let { skipped ->
            HighlightTile(
                label = stringResource(R.string.jam_recap_most_skipped),
                value = "${skipped.title} · ${skipped.artist}",
                emblem = { Icon(Icons.Rounded.SkipNext, null, tint = colors.onSecondary, modifier = Modifier.size(20.dp)) },
                emblemColor = colors.secondary,
                emblemShape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
private fun HighlightTile(
    label: String,
    value: String,
    emblem: @Composable () -> Unit,
    emblemColor: Color,
    emblemShape: Shape
) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(emblemColor, emblemShape)) {
            emblem()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The songs in the order they played: a few, then all of them on request. */
@Composable
internal fun RecapTrackList(recap: JamRecap) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) recap.tracks else recap.tracks.take(PREVIEW_TRACKS)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEachIndexed { index, track ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(width = 24.dp, height = 20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        // Blank when whoever queued it has since deleted their account.
                        text = if (track.addedBy.isBlank()) track.artist
                        else stringResource(R.string.jam_recap_track_added_by, track.artist, track.addedBy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (expanded && recap.tracksOmitted > 0) {
            val omitted = recap.tracksOmitted.toInt()
            Text(
                text = pluralStringResource(R.plurals.jam_recap_omitted, omitted, omitted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (recap.tracks.size > PREVIEW_TRACKS) {
            TextButton(onClick = { expanded = !expanded }, shapes = ButtonDefaults.shapes()) {
                Text(
                    if (expanded) stringResource(R.string.jam_recap_show_less)
                    else pluralStringResource(R.plurals.jam_recap_show_all, recap.tracks.size, recap.tracks.size)
                )
            }
        }
    }
}
