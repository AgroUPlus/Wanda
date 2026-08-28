package com.wander.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType

@Composable
internal fun ImportIdleContent(
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    detectedPlatform: PlatformType?,
    onPaste: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Paste a link from Spotify, Deezer, YouTube Music, Apple Music, or raw text lines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = inputUrl,
            onValueChange = onUrlChange,
            placeholder = { Text("https://open.spotify.com/playlist/...") },
            trailingIcon = {
                IconButton(onClick = onPaste) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard")
                }
            },
            singleLine = false,
            maxLines = 3,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = detectedPlatform != null) {
            detectedPlatform?.let { platform ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        com.wander.android.ui.screens.importer.PlatformIcon(
                            platform = platform,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Detected: ${platform.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportProgressContent(progress: ImportProgress) {
    when (progress) {
        is ImportProgress.Fetching -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Reading playlist from ${progress.platform.displayName}...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        is ImportProgress.Matching -> {
            val targetProgress = if (progress.total > 0) progress.current.toFloat() / progress.total else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "matchingProgress"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Matching track ${progress.current} of ${progress.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${progress.matchedCount} found",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.currentTrackName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is ImportProgress.Saving -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Saving \"${progress.playlistName}\" to your library...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is ImportProgress.Success -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Playlist imported! 🎉",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\"${progress.playlistName}\" • ${progress.matchedCount} of ${progress.totalCount} tracks matched",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is ImportProgress.Failed -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Import failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = progress.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {}
    }
}
