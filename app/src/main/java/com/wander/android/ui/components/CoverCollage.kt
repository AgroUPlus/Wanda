package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Up to four covers in one square, so an offer shows *what* is on offer rather than an icon.
 *
 * The layout follows how many pictures there actually are: one fills the square, two split it,
 * three or four make a grid. Padding a short list out to a fixed grid would leave holes that read
 * as failed image loads.
 *
 * With no covers at all it falls back to the download glyph. That is the honest case rather than a
 * rare one — Agro indexes tags, not artwork, so a cover only exists here when the same recording
 * is already known to the library from another source.
 */
@Composable
internal fun CoverCollage(
    urls: List<String>,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        when (urls.size) {
            0 -> Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size / 2)
            )
            1 -> Tile(urls[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                urls.forEach { Tile(it, Modifier.weight(1f).fillMaxSize()) }
            }
            else -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Tile(urls[0], Modifier.weight(1f).fillMaxSize())
                    Tile(urls[1], Modifier.weight(1f).fillMaxSize())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Tile(urls[2], Modifier.weight(1f).fillMaxSize())
                    // A fourth is optional: three covers leave the last quarter as plain surface,
                    // which reads as part of the tile rather than as a missing image.
                    urls.getOrNull(3)?.let { Tile(it, Modifier.weight(1f).fillMaxSize()) }
                        ?: Box(Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Tile(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}
