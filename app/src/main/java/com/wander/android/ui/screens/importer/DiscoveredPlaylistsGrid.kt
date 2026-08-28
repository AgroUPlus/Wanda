package com.wander.android.ui.screens.importer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawUserPlaylistSummary
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.scrollingTitle

@Composable
fun DiscoveredPlaylistsGrid(
    platform: PlatformType,
    playlists: List<RawUserPlaylistSummary>,
    onSelectPlaylist: (RawUserPlaylistSummary) -> Unit,
    onRelog: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ExpressiveConnectedBanner(
                platform = platform,
                count = playlists.size,
                onRelog = onRelog,
                onLogout = onLogout
            )
        }

        items(playlists, key = { it.id }) { item ->
            DiscoveredPlaylistCard(
                summary = item,
                onClick = { onSelectPlaylist(item) }
            )
        }
    }
}

@Composable
private fun ExpressiveConnectedBanner(
    platform: PlatformType,
    count: Int,
    onRelog: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 28.dp
        ),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PlatformIcon(
                    platform = platform,
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Connected to ${platform.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "$count playlists discovered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                M3MorphingButton(
                    onClick = onRelog,
                    isSecondary = false,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Web view",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                M3MorphingButton(
                    onClick = onLogout,
                    isSecondary = true,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Switch account",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredPlaylistCard(
    summary: RawUserPlaylistSummary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, label = "discoveredPlaylistPress")

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Artwork(
                url = summary.coverUrl,
                contentDescription = summary.name,
                sizeDp = 136.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            if (!summary.description.isNullOrBlank()) {
                Text(
                    text = summary.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun M3MorphingButton(
    onClick: () -> Unit,
    isSecondary: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 28.dp else 14.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "btnCornerMorph"
    )

    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSecondary) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
            contentColor = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        ),
        interactionSource = interactionSource,
        // No `shapes` here: this button already animates its own corner radius, which is the same
        // expressive idea done by hand. Passing both is a compile error, and the hand-rolled morph
        // is the more specific behaviour.
        modifier = modifier
    ) {
        content()
    }
}
