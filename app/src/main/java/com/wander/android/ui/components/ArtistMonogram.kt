package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * An artist's initial over a tinted ground, for when nobody has published their photograph.
 *
 * This exists because every alternative was worse, and there were two of them.
 *
 * The header used to fall back to a cover off one of the artist's records — their own sleeve at
 * best, and a sleeve is not a face. Underneath that, Navidrome's own portrait came from Last.fm,
 * which matches artists *by name* and so returns a correct name and biography beside a photograph
 * of a different person, with nothing in the response admitting it. Neither could be validated.
 *
 * A wrong photograph of a real person is a claim; a letter is not. See `NavidromeSource.getArtist`.
 *
 * The tint is derived from the name, so one artist keeps the same colour everywhere they appear and
 * two artists next to each other do not come out identical. Hue only — the surface it blends into
 * and the text over it stay theme colours, so this reads correctly in light and dark without a
 * second palette.
 */
@Composable
fun ArtistMonogram(
    name: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // Three theme tones rather than three arbitrary colours: whichever pair the name lands on is
    // guaranteed to sit correctly against the page and under `onSurface` text.
    val grounds = listOf(
        scheme.primaryContainer to scheme.secondaryContainer,
        scheme.secondaryContainer to scheme.tertiaryContainer,
        scheme.tertiaryContainer to scheme.primaryContainer
    )
    val ground = grounds[(name.hashCode().ushr(1)) % grounds.size]

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(ground.first, ground.second)))
    ) {
        Text(
            text = monogramOf(name),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = scheme.onSurface
        )
    }
}

/**
 * The first letter of the name, or the first two initials when it reads as two words.
 *
 * Code points rather than chars: a `Char` is half of anything outside the basic plane, and an
 * artist whose name begins with an emoji or a non-BMP script would otherwise draw a broken glyph.
 */
internal fun monogramOf(name: String): String {
    val words = name.trim().split(' ', '\t').filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    val initials = words.take(2).map { word ->
        val first = word.codePointAt(0)
        String(Character.toChars(first)).uppercase()
    }
    return if (initials.size == 2 && words.size >= 2) initials.joinToString("") else initials.first()
}
