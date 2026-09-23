package com.wander.android.ui.screens.replay

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon

/**
 * How a card looks before anything is on it: one flat colour, the colour that reads on it, and the
 * big Material shape tumbling behind the content.
 *
 * Every colour is a role from the dynamic scheme, so the story is painted in the wallpaper's
 * colours and stays legible in dark theme without a second table. Neighbouring cards alternate
 * between the strong roles and their containers so each swipe is a clear change of colour, never
 * two near-identical tints in a row.
 */
@Immutable
internal data class ReplayPalette(
    val container: Color,
    val content: Color,
    val shape: RoundedPolygon
)

/**
 * One card to one palette. Exhaustive like the card renderer, so a new card cannot quietly inherit
 * somebody else's colours.
 */
@Composable
@ReadOnlyComposable
internal fun replayPaletteFor(card: ReplayCard): ReplayPalette {
    val c = MaterialTheme.colorScheme
    return when (card) {
        is ReplayCard.Intro -> ReplayPalette(c.primary, c.onPrimary, MaterialShapes.VerySunny)
        is ReplayCard.Minutes ->
            ReplayPalette(c.tertiaryContainer, c.onTertiaryContainer, MaterialShapes.Cookie12Sided)
        is ReplayCard.Shape -> ReplayPalette(c.secondary, c.onSecondary, MaterialShapes.Arch)
        is ReplayCard.Hours ->
            ReplayPalette(c.primaryContainer, c.onPrimaryContainer, MaterialShapes.SoftBurst)
        is ReplayCard.TopArtists -> ReplayPalette(c.tertiary, c.onTertiary, MaterialShapes.Flower)
        is ReplayCard.TopSong ->
            ReplayPalette(c.secondaryContainer, c.onSecondaryContainer, MaterialShapes.Sunny)
        is ReplayCard.Genres -> ReplayPalette(c.primary, c.onPrimary, MaterialShapes.Puffy)
        is ReplayCard.Discovery ->
            ReplayPalette(c.tertiaryFixed, c.onTertiaryFixed, MaterialShapes.Clover8Leaf)
        is ReplayCard.Streak -> ReplayPalette(c.error, c.onError, MaterialShapes.Burst)
        is ReplayCard.Devices ->
            ReplayPalette(c.secondaryFixed, c.onSecondaryFixed, MaterialShapes.Cookie6Sided)
        is ReplayCard.Charts -> ReplayPalette(c.inverseSurface, c.inverseOnSurface, MaterialShapes.Boom)
        is ReplayCard.Circle -> ReplayPalette(c.primaryFixed, c.onPrimaryFixed, MaterialShapes.Clover4Leaf)
        is ReplayCard.Silence ->
            ReplayPalette(c.surfaceVariant, c.onSurfaceVariant, MaterialShapes.Ghostish)
        // The outro carries the only real buttons in the story, so it sits on a surface they were
        // designed for rather than on a colour that would swallow a filled primary button.
        is ReplayCard.Outro ->
            ReplayPalette(c.surfaceContainerHigh, c.onSurface, MaterialShapes.Heart)
    }
}

/**
 * The flat colour behind the current card, for the few things drawn *inverted* on it — a badge
 * filled in the content colour needs its text cut out in this one.
 */
internal val LocalReplayContainer = compositionLocalOf { Color.Unspecified }

/** Secondary text on a card: the card's own content colour, stepped back. */
internal val replayMuted: Color
    @Composable @ReadOnlyComposable get() = LocalContentColor.current.copy(alpha = MutedAlpha)

private const val MutedAlpha = 0.74f
