package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.PlaylistVisibility

/**
 * Every way this playlist can leave the device. Any playlist can go as a link or a file, whatever
 * backend it is on; Agro appears once a server is paired, and becomes "who can open it" once this
 * playlist has a copy there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistShareSheet(
    /** The backend's name when it can mint its own link for this playlist; null otherwise. */
    originalSource: String?,
    canUseAgro: Boolean,
    publishedVisibility: PlaylistVisibility?,
    onOriginal: () -> Unit,
    onLink: () -> Unit,
    onFile: () -> Unit,
    onAgro: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_share_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            originalSource?.let { source ->
                ShareOption(
                    icon = Icons.Rounded.OpenInNew,
                    title = stringResource(R.string.playlist_share_source),
                    description = stringResource(R.string.playlist_share_source_desc, source),
                    onClick = onOriginal
                )
            }
            ShareOption(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.playlist_share_link),
                description = stringResource(R.string.playlist_share_link_desc),
                onClick = onLink
            )
            ShareOption(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.playlist_share_file),
                description = stringResource(R.string.playlist_share_file_desc),
                onClick = onFile
            )
            if (canUseAgro) {
                if (publishedVisibility == null) {
                    ShareOption(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.playlist_share_agro),
                        description = stringResource(R.string.playlist_share_agro_desc),
                        onClick = onAgro
                    )
                } else {
                    ShareOption(
                        icon = Icons.Rounded.Visibility,
                        title = stringResource(R.string.playlist_visibility_action),
                        description = stringResource(visibilityTitle(publishedVisibility)),
                        onClick = onAgro
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
