package com.wander.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.FingerprintStatus

/**
 * Whether a track has been measured, as a dot beside its title.
 *
 * A dot rather than an icon, and six pixels of it. This answers a question almost nobody is asking
 * at any given moment — it matters when you are wondering why hum-to-search cannot find a song you
 * own — and it must not compete with the title for attention the rest of the time. An icon at a
 * legible size would be a second thing to read on every row of a library of thousands.
 *
 * Green means both indexes exist: the landmark fingerprint *and* the melody contour. Blue means it
 * is being decoded right now. Red means neither, or only one — a track with half its measurements
 * is a track the other half of the feature cannot find, so it does not get to look finished.
 *
 * Colour alone carries the meaning, which is normally a thing to avoid; the content description is
 * how that is answered for anyone who cannot use it, and the three hues are picked to stay
 * distinguishable under the common red-green deficiencies by differing in lightness as well as hue.
 */
@Composable
fun FingerprintBadge(
    status: FingerprintStatus,
    modifier: Modifier = Modifier
) {
    // Not taken from the colour scheme: these are a traffic light, and their meaning is the hue
    // itself rather than a role in the theme. A tertiary that happened to be pink would say nothing.
    // Two sets, because a tone legible on white is muddy on near-black and the reverse.
    val dark = isSystemInDarkTheme()
    val target = when (status) {
        FingerprintStatus.INDEXED -> if (dark) IndexedDark else IndexedLight
        FingerprintStatus.PROCESSING -> if (dark) ProcessingDark else ProcessingLight
        FingerprintStatus.MISSING -> if (dark) MissingDark else MissingLight
    }
    // Animated, so a track finishing its decode fades from blue to green rather than blinking — a
    // hard swap on a list that updates as the worker moves through it reads as flicker.
    val colour by animateColorAsState(target, label = "fingerprintBadge")

    Box(
        modifier = modifier
            .size(DotSize)
            .background(colour, CircleShape)
            .semantics {
                contentDescription = when (status) {
                    FingerprintStatus.INDEXED -> "Fingerprinted"
                    FingerprintStatus.PROCESSING -> "Fingerprinting now"
                    FingerprintStatus.MISSING -> "Not fingerprinted"
                }
            }
    )
}

private val DotSize = 6.dp

private val IndexedLight = Color(0xFF2E7D32)
private val IndexedDark = Color(0xFF81C784)
private val ProcessingLight = Color(0xFF1565C0)
private val ProcessingDark = Color(0xFF64B5F6)
private val MissingLight = Color(0xFFC62828)
private val MissingDark = Color(0xFFE57373)

/**
 * The same three states as [FingerprintBadge], on Now Playing, as a fingerprint.
 *
 * An icon rather than a bare dot because this one stands alone. In a list the dot sits beside a
 * title and inherits its meaning from the column it is in; here there is nothing next to it to say
 * what it is about, and an unexplained coloured dot beside a song title reads like a status nobody
 * can decode.
 *
 * ## It is about the track on screen, and nothing else
 *
 * Green means *this* song is measured, blue means *this* song is being measured right now, red
 * means it is not and is about to be — playing an unmeasured track is what schedules it. Reading
 * anything else here would be a lie about the song whose title it sits beside; whether the
 * library-wide sweep happens to be running is a different question and belongs on the fingerprints
 * screen, which answers it per group.
 *
 * The content description says which, because none of that is recoverable from a colour.
 */
@Composable
fun NowPlayingFingerprint(
    status: FingerprintStatus,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val busy = status == FingerprintStatus.PROCESSING

    val target = when {
        busy -> if (dark) ProcessingDark else ProcessingLight
        status == FingerprintStatus.INDEXED -> if (dark) IndexedDark else IndexedLight
        else -> if (dark) MissingDark else MissingLight
    }
    val colour by animateColorAsState(target, label = "nowPlayingFingerprint")

    // Breathing rather than blinking, and only while there is something to report. A hard blink on
    // a screen someone is looking at for minutes at a time is an irritation, not information.
    val transition = rememberInfiniteTransition(label = "fingerprintPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "fingerprintPulseAlpha"
    )

    Icon(
        imageVector = Icons.Rounded.Fingerprint,
        contentDescription = when {
            busy -> "Measuring this track now"
            status == FingerprintStatus.INDEXED -> "This track is fingerprinted"
            else -> "This track is not fingerprinted yet"
        },
        tint = colour,
        modifier = modifier
            .size(IconSize)
            .alpha(if (busy) pulse else RestingAlpha)
    )
}

private val IconSize = 18.dp

/** Held back at rest so the settled states sit behind the title rather than beside it. */
private const val RestingAlpha = 0.6f
