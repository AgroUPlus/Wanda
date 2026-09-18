package com.wander.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.model.SourceType
import com.wander.android.ui.screens.importer.PlatformIcon
import com.wander.android.ui.theme.LocalDeviceAmber
import com.wander.android.ui.theme.NavidromeBlue
import com.wander.android.ui.theme.YtMusicCoral

/** Each source's identity colour — see the constants in `ui/theme/Color.kt` for why these hues. */
internal val SourceType.color: Color
    get() = when (this) {
        SourceType.NAVIDROME -> NavidromeBlue
        SourceType.YTMUSIC -> YtMusicCoral
        SourceType.LOCAL -> LocalDeviceAmber
    }

/** A distinct outline per source, on top of the colour, so two badges are never told apart by hue
 *  alone — the same trick the settings hub plays across its seven categories. */
private val SourceType.badgeShape
    @Composable get() = when (this) {
        SourceType.NAVIDROME -> MaterialShapes.Cookie4Sided
        SourceType.YTMUSIC -> MaterialShapes.Circle
        SourceType.LOCAL -> MaterialShapes.Square
    }.toShape()

/**
 * The one place a source's icon and colour are decided — everywhere a source needs to be told
 * apart at a glance (filter chips, the source picker, the Connections rows) draws this instead of
 * inventing its own badge.
 */
@Composable
fun SourceIcon(source: SourceType, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val hue = source.color
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(hue.copy(alpha = BadgeAlpha), source.badgeShape)
    ) {
        val glyphSize = size * GlyphFraction
        when (source) {
            // Navidrome's own mark, used to name Navidrome/Subsonic — a trademark's actual job.
            SourceType.NAVIDROME -> Image(
                painter = painterResource(R.drawable.navidrome_logo),
                contentDescription = null,
                modifier = Modifier.size(glyphSize)
            )
            // The app already draws this mark, tinted, for the playlist importer — reused so the
            // two can never diverge.
            SourceType.YTMUSIC -> PlatformIcon(platform = PlatformType.YOUTUBE, size = glyphSize, tint = hue)
            SourceType.LOCAL -> Icon(
                imageVector = Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(glyphSize)
            )
        }
    }
}

private const val BadgeAlpha = 0.16f
private const val GlyphFraction = 0.55f
