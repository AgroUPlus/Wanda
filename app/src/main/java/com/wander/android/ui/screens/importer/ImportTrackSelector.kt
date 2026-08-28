package com.wander.android.ui.screens.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.ui.components.Artwork

@Composable
fun ImportTrackSelector(
    playlist: RawImportPlaylist,
    selectedIndices: Set<Int>,
    onToggleTrack: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirmImport: (customTitle: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editedTitle by remember(playlist.title) { mutableStateOf(playlist.title) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Playlist Header with editable title
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Artwork(
                        url = playlist.coverUrl,
                        contentDescription = null,
                        sizeDp = 52.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            label = { Text("Playlist Title") },
                            singleLine = true,
                            trailingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit Title", modifier = Modifier.size(16.dp))
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${playlist.tracks.size} tracks found from ${playlist.platform.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Selection Control Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${selectedIndices.size} of ${playlist.tracks.size} selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                TextButton(onClick = onSelectAll, shapes = ButtonDefaults.shapes()) { Text("All") }
                TextButton(onClick = onDeselectAll, shapes = ButtonDefaults.shapes()) { Text("None") }
            }
        }

        // Track Checklist
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(playlist.tracks, key = { idx, _ -> idx }) { index, track ->
                val isSelected = selectedIndices.contains(index)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTrack(index) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleTrack(index) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirmImport(editedTitle) },
                    enabled = selectedIndices.isNotEmpty(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Import Selected (${selectedIndices.size})")
                }
            }
        }
    }
}
