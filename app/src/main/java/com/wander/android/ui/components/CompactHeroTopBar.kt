package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * A compact bar that fades in once a screen's hero header has scrolled out of view, so the
 * cover/title/play controls stay reachable without scrolling back to the top.
 *
 * The back button stays opaque throughout, matching the screen's pre-scroll floating back button;
 * only the bar's background, thumbnail, title and play button fade in past [visibleFraction] > 0,
 * tracking scroll position directly rather than a separate spring — the same way a native
 * large-title bar's collapse is scroll-linked, not sprung.
 */
@Composable
fun CompactHeroTopBar(
    visibleFraction: () -> Float,
    onBack: () -> Unit,
    title: String,
    artworkUrl: String?,
    onPlay: () -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 56.dp)
                .graphicsLayer { alpha = visibleFraction() }
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(56.dp)
                .padding(horizontal = 4.dp)
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp)
                    .graphicsLayer { alpha = visibleFraction() }
            ) {
                Artwork(
                    url = artworkUrl,
                    contentDescription = null,
                    sizeDp = 36.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }

            FilledIconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.graphicsLayer { alpha = visibleFraction() }
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play)
                )
            }
        }
    }
}
