package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wander.android.core.playback.AudioTrackInfo

/**
 * Lets the user choose one of the audio language tracks the current media item carries.
 *
 * The dialog is shown only when two or more distinct language tracks are available (the button
 * that opens it is gated on `audioTracks.size > 1`), so every row here is a real alternative.
 *
 * Choosing a track calls [onSelect] with the selected [AudioTrackInfo] and lets the caller close
 * the dialog; the active row has a check mark and is not tappable, matching the pattern in
 * [SourcePickerDialog].
 *
 * The selection is persisted and applies to every subsequent source via
 * [PlayerConnection.setPreferredAudioLanguage].
 */
@Composable
internal fun AudioTrackPickerDialog(
    audioTracks: List<AudioTrackInfo>,
    onSelect: (AudioTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Audio language",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = "Applies to every source by default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Box(modifier = Modifier.height(8.dp))

                audioTracks.forEach { track ->
                    AudioTrackRow(
                        track = track,
                        isCurrent = track.isSelected,
                        onClick = { onSelect(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    track: AudioTrackInfo,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The active track is not tappable — choosing it again would do nothing.
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = track.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Currently playing",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Human-readable name for this audio track.
 *
 * Preference order: explicit [AudioTrackInfo.label] > language tag > fallback string.
 * A track without either is uncommon enough that "Unknown" is an honest label — it avoids a
 * blank row in the picker and makes it clear the stream carries no metadata.
 */
private val AudioTrackInfo.displayName: String
    get() = when {
        !label.isNullOrBlank() -> label
        !language.isNullOrBlank() -> language.uppercase()
        else -> "Unknown"
    }
