package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.PlaylistPublication

/** What the share sheet can do; a null action is one this playlist does not offer. */
internal class PlaylistShareChoices(
    val onOriginal: (() -> Unit)?,
    val onLink: () -> Unit,
    val onFile: () -> Unit,
    /** Publish to Agro. Null when no server is paired or the playlist is already there. */
    val onPublish: (() -> Unit)?,
    val onResend: () -> Unit,
    val onChangeVisibility: () -> Unit,
    val onUnshare: () -> Unit
)

/**
 * Every way this playlist can leave the device. Any playlist can go as a link or a file, whatever
 * backend it is on. Once it is on Agro the sheet leads with that copy — send its link again, change
 * who can open it, or stop sharing — so sharing again never makes a second copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistShareSheet(
    /** The backend's name when it can mint its own link for this playlist; null otherwise. */
    originalSource: String?,
    publication: PlaylistPublication?,
    choices: PlaylistShareChoices,
    onDismiss: () -> Unit
) {
    // Opened in full: half-expanded, the options at the bottom would sit below the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (publication != null) {
                SheetHeading(stringResource(R.string.playlist_shared_on_agro))
                val visibility = stringResource(visibilityTitle(publication.visibility))
                ShareOption(
                    icon = Icons.Rounded.Send,
                    title = stringResource(R.string.playlist_share_agro_resend),
                    description = stringResource(R.string.playlist_share_agro_resend_desc, visibility),
                    onClick = choices.onResend
                )
                ShareOption(
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.playlist_visibility_action),
                    description = visibility,
                    onClick = choices.onChangeVisibility
                )
                ShareOption(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.playlist_unshare),
                    description = stringResource(R.string.playlist_unshare_desc),
                    onClick = choices.onUnshare,
                    danger = true
                )
                SheetHeading(stringResource(R.string.playlist_share_other))
            } else {
                SheetHeading(stringResource(R.string.playlist_share_title))
            }
            originalSource?.let { source ->
                choices.onOriginal?.let { original ->
                    ShareOption(
                        icon = Icons.Rounded.OpenInNew,
                        title = stringResource(R.string.playlist_share_source),
                        description = stringResource(R.string.playlist_share_source_desc, source),
                        onClick = original
                    )
                }
            }
            ShareOption(
                icon = Icons.Rounded.Link,
                title = stringResource(R.string.playlist_share_link),
                description = stringResource(R.string.playlist_share_link_desc),
                onClick = choices.onLink
            )
            ShareOption(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.playlist_share_file),
                description = stringResource(R.string.playlist_share_file_desc),
                onClick = choices.onFile
            )
            choices.onPublish?.let { publish ->
                ShareOption(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.playlist_share_agro),
                    description = stringResource(R.string.playlist_share_agro_desc),
                    onClick = publish
                )
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ShareOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
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
                color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
