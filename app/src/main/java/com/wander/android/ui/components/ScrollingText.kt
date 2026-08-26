package com.wander.android.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/**
 * Scrolls a single-line label that does not fit, instead of cutting it off.
 *
 * Ellipsis is fine for a subtitle you can guess at, but a track title is the one thing on the
 * screen you actually need to read, and "Everything In Its Right Pl…" is not it.
 *
 * Applied to titles and artist lines wherever a track is named. It costs nothing on the rows that
 * fit: `basicMarquee` only animates text that actually overflows, so a list of short titles never
 * starts an animation at all.
 *
 * Not applied to body copy, descriptions or anything that is allowed more than one line — those
 * wrap, which is a better answer than scrolling.
 *
 * [MarqueeAnimationMode.Immediately] rather than the default `WhileFocused`: nothing in a music
 * player takes focus, so the default would never run at all.
 */
internal fun Modifier.scrollingTitle(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    animationMode = MarqueeAnimationMode.Immediately,
    repeatDelayMillis = REPEAT_DELAY_MS,
    initialDelayMillis = INITIAL_DELAY_MS
)

/** Long enough to read the start of the title before it moves. */
private const val INITIAL_DELAY_MS = 1_500

/** And long enough to read it again at the end of each pass. */
private const val REPEAT_DELAY_MS = 2_000
