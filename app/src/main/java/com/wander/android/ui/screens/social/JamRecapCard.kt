package com.wander.android.ui.screens.social

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.StoredJamRecap
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.screens.stats.formatListeningTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** The most faces the header stacks before the rest are left to the subtitle. */
private const val MAX_FACES = 4

/**
 * What a jam was, once it is over: how long, with whom, who ran the decks, and what the room loved
 * and could not wait to be rid of — then the songs themselves, and a way to keep them.
 */
@Composable
internal fun JamRecapCard(
    stored: StoredJamRecap,
    saved: Boolean,
    onSave: (title: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recap = stored.recap
    val colors = MaterialTheme.colorScheme
    val playlistName = stringResource(R.string.jam_recap_playlist_name, recapDate(recap.endedAt))

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp).animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Faces(recap.people.take(MAX_FACES))
                Spacer(Modifier.width(12.dp))
                RecapBadge()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.jam_recap_dismiss))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.jam_recap_together, formatListeningTime(recap.durationMs / 1000)),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                val count = recap.tracks.size + recap.tracksOmitted.toInt()
                Text(
                    text = pluralStringResource(
                        R.plurals.jam_recap_songs_with,
                        count,
                        count,
                        recap.people.joinToString { "@$it" }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            RecapHighlights(recap)
            RecapTrackList(recap)

            Button(
                onClick = { onSave(playlistName) },
                enabled = !saved,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (saved) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(if (saved) R.string.jam_recap_saved else R.string.jam_recap_save))
            }
        }
    }
}

/** Everyone in the room, overlapped like people standing together. */
@Composable
private fun Faces(people: List<String>) {
    Box {
        people.forEachIndexed { index, name ->
            CuteAvatar(
                seed = name,
                size = 36.dp,
                shape = PersonShape,
                showBorder = true,
                borderColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.offset(x = (index * 24).dp)
            )
        }
        // The stack is drawn with offsets, which take no space; this claims what it covers.
        Spacer(Modifier.width((36 + (people.size - 1).coerceAtLeast(0) * 24).dp))
    }
}

@Composable
private fun RecapBadge() {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.tertiaryContainer, CircleShape)
            .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
    ) {
        Icon(Icons.Rounded.Groups, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.jam_recap_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = colors.onTertiaryContainer
        )
    }
}

/**
 * The day the jam ended, in the reader's own date format.
 *
 * The server writes RFC 3339 and nothing else, so a value that does not parse is a server bug; it
 * is shown as it came rather than replaced with a date that would be a guess.
 */
private fun recapDate(endedAt: String): String = try {
    OffsetDateTime.parse(endedAt).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: DateTimeParseException) {
    endedAt
}
