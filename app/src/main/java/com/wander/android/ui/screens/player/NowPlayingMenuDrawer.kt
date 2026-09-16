package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.scrollingTitle

/**
 * Contextual menu drawer for the Now Playing screen, replacing the former top-right queue button.
 * Unifies access to the queue, playlist actions, radio mode, speed/pitch adjustments, and track actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingMenuDrawer(
    track: UnifiedTrack,
    isLiked: Boolean,
    isRadioMode: Boolean,
    queueSize: Int,
    canSwitchSource: Boolean,
    canShare: Boolean,
    canAddToPlaylist: Boolean,
    hasMultipleAudioTracks: Boolean,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleRadio: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: (() -> Unit)?,
    onOpenSourcePicker: (() -> Unit)?,
    onOpenAudioTrackPicker: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onJamAction: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Track Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Artwork(
                    url = track.artworkUrl,
                    contentDescription = null,
                    sizeDp = 48.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(48.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Primary Queue & Radio Actions
            MenuDrawerAction(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = if (queueSize > 0) "${stringResource(R.string.action_queue)} ($queueSize)" else stringResource(R.string.action_queue),
                onClick = { onOpenQueue(); onDismiss() }
            )

            if (canAddToPlaylist) {
                MenuDrawerAction(
                    icon = Icons.Rounded.LibraryAdd,
                    label = stringResource(R.string.action_add_to_playlist),
                    onClick = { onAddToPlaylist(); onDismiss() }
                )
            }

            MenuDrawerAction(
                icon = Icons.Rounded.Radio,
                label = if (isRadioMode) stringResource(R.string.action_radio_mode) + " (active)" else stringResource(R.string.action_start_radio),
                tint = if (isRadioMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onToggleRadio(); onDismiss() }
            )

            MenuDrawerAction(
                icon = Icons.Rounded.Speed,
                label = stringResource(R.string.action_speed_and_pitch),
                onClick = { onOpenSpeedPitch(); onDismiss() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Secondary playback actions
            MenuDrawerAction(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.action_play_next),
                onClick = { onPlayNext(); onDismiss() }
            )
            MenuDrawerAction(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = stringResource(R.string.action_add_to_queue),
                onClick = { onAddToQueue(); onDismiss() }
            )

            if (canSwitchSource && onOpenSourcePicker != null) {
                MenuDrawerAction(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "${stringResource(R.string.action_switch_source)} (${track.source.displayName})",
                    onClick = { onOpenSourcePicker(); onDismiss() }
                )
            }

            if (hasMultipleAudioTracks && onOpenAudioTrackPicker != null) {
                MenuDrawerAction(
                    icon = Icons.Rounded.Translate,
                    label = stringResource(R.string.action_audio_language),
                    onClick = { onOpenAudioTrackPicker(); onDismiss() }
                )
            }

            onOpenArtist?.let { openArtist ->
                MenuDrawerAction(
                    icon = Icons.Rounded.Person,
                    label = "${stringResource(R.string.action_go_to_artist)} (${track.artist})",
                    onClick = { openArtist(); onDismiss() }
                )
            }

            onOpenAlbum?.let { openAlbum ->
                MenuDrawerAction(
                    icon = Icons.Rounded.Album,
                    label = "${stringResource(R.string.action_go_to_album)} (${track.album})",
                    onClick = { openAlbum(); onDismiss() }
                )
            }

            onJamAction?.let { jamAction ->
                MenuDrawerAction(
                    icon = Icons.Rounded.Groups,
                    label = stringResource(R.string.action_add_to_jam),
                    onClick = { jamAction(); onDismiss() }
                )
            }

            if (canShare && onShare != null) {
                MenuDrawerAction(
                    icon = Icons.Rounded.Share,
                    label = stringResource(R.string.action_share),
                    onClick = { onShare(); onDismiss() }
                )
            }

            MenuDrawerAction(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(if (isLiked) R.string.action_unlike else R.string.action_like),
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onToggleLike(); onDismiss() }
            )
        }
    }
}

@Composable
private fun MenuDrawerAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
