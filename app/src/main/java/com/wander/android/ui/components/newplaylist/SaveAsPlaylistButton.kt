package com.wander.android.ui.components.newplaylist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * Saves [tracks] — the queue, in its order — as a new playlist, through the same sheet every other
 * new playlist is made in. The tracks are read when the sheet's Create is pressed, not when the
 * button is, so a track that finished in between is still in it.
 *
 * A Blend is not offered: Agro chooses a Blend's songs and would replace these.
 */
@Composable
internal fun SaveAsPlaylistButton(tracks: List<UnifiedTrack>) {
    var naming by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { naming = true }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = stringResource(R.string.queue_save_as_playlist),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (naming) {
        NewPlaylistSheet(
            onCreated = { naming = false },
            onDismiss = { naming = false },
            tracks = tracks,
            plainSource = plainSourceFor(tracks),
            allowBlend = false
        )
    }
}

/**
 * On Navidrome when every track came from it, so the playlist is there for its other clients too;
 * a Wanda playlist otherwise, since that is the one kind that holds tracks from any source.
 */
internal fun plainSourceFor(tracks: List<UnifiedTrack>): SourceType =
    if (tracks.isNotEmpty() && tracks.all {
            it.source == SourceType.NAVIDROME
        }
    ) {
        SourceType.NAVIDROME
    } else {
        SourceType.LOCAL
    }
