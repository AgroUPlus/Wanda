package com.wander.android.ui.screens.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * What the backend says about the artist.
 *
 * Collapsed to a few lines by default and expanded by tapping it. A bio is prose of unbounded
 * length — Last.fm's run to several paragraphs — and left uncut it pushes the discography, which
 * is what the page is for, off the bottom of the screen.
 *
 * No "read more" affordance: the whole block is the target, and a link-shaped control under a
 * paragraph of prose reads like a link out to somewhere else, which this is not.
 *
 * [genres] is almost always empty — see `ArtistDetails.genres` for the one path that fills it in —
 * and drawn above the bio rather than mixed into it, since it is tags from a different source
 * (MusicBrainz) answering a different question (what kind) than the bio's own prose does (who).
 */
@Composable
internal fun ArtistBio(bio: String?, genres: List<String> = emptyList(), modifier: Modifier = Modifier) {
    var expanded by remember(bio) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        // Null when genres are the only thing this page has to say — a MusicBrainz fallback with
        // no disambiguation text, still worth the genre line above, still with no prose to show.
        bio?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else CollapsedLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Enough to tell what kind of artist this is; not enough to bury the records. */
private const val CollapsedLines = 4
