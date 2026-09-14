package com.wander.android.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.importer.PlatformType
import com.wander.android.ui.screens.importer.PlatformIcon

/**
 * The badge on a settings row that names a *thing* rather than a preference.
 *
 * ## Why only some rows have one
 *
 * Connections and Sync list four identities — a server, an account, this phone, a paired device —
 * and an icon is how you find the one you came for without reading. The toggles beside them
 * ("Sync settings with Agro", "Contribute to Popular on Agro") are not identities, and giving them
 * glyphs too would produce a ragged column of invented symbols that say nothing the label does not
 * already say. Material's list guidance is the same: a leading icon earns its place by
 * distinguishing items, not by filling the gutter.
 *
 * ## Why the colour comes from the category
 *
 * The hub's seven hues are how a reader knows which page they are on, and four brand colours
 * introduced here would compete with that — Connections would stop looking like Connections. So a
 * badge takes its parent category's hue, exactly as the hub's own badge does, at low alpha with the
 * glyph at full strength over it. One colour, two opacities, no light/dark variant needed.
 *
 * The shapes are per-identity rather than per-category, which is what keeps four same-coloured
 * badges from reading as one repeated thing — the same trick the hub plays with [MaterialShapes].
 */
@Composable
internal fun SourceBadge(
    identity: SourceIdentity,
    hue: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BadgeSize)
            .background(hue.copy(alpha = BadgeAlpha), identity.shape())
    ) {
        when (identity) {
            // Full colour, alone among these. This is somebody's actual logo rather than a glyph
            // chosen to stand for them, and recolouring a logo to match a palette is the one
            // liberty not to take with it.
            SourceIdentity.AGRO -> Image(
                painter = painterResource(R.drawable.agro_logo),
                contentDescription = null,
                modifier = Modifier.size(GlyphSize)
            )

            // The app already draws this mark, tinted, for the playlist importer. Reused rather
            // than redrawn so the two can never diverge.
            SourceIdentity.YOUTUBE_MUSIC -> PlatformIcon(
                platform = PlatformType.YOUTUBE,
                size = GlyphSize,
                tint = hue
            )

            // Navidrome's own mark, used to name Navidrome — which is what a trademark is for.
            // Full colour for the same reason the Agro logo is.
            SourceIdentity.NAVIDROME -> Image(
                painter = painterResource(R.drawable.navidrome_logo),
                contentDescription = null,
                modifier = Modifier.size(GlyphSize)
            )

            SourceIdentity.LOCAL -> Icon(
                imageVector = Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(GlyphSize)
            )
        }
    }
}

/** The four things settings rows name: three backends and the paired device. */
internal enum class SourceIdentity { NAVIDROME, YOUTUBE_MUSIC, LOCAL, AGRO }

@Composable
private fun SourceIdentity.shape() = when (this) {
    SourceIdentity.NAVIDROME -> MaterialShapes.Cookie4Sided
    SourceIdentity.YOUTUBE_MUSIC -> MaterialShapes.Circle
    SourceIdentity.LOCAL -> MaterialShapes.Square
    SourceIdentity.AGRO -> MaterialShapes.SoftBurst
}.toShape()

/** Smaller than the hub's badge, so a row inside a page reads as subordinate to the page itself. */
private val BadgeSize = 40.dp
private val GlyphSize = 22.dp
private const val BadgeAlpha = 0.16f
