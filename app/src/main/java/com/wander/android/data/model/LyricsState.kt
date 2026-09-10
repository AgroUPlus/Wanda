package com.wander.android.data.model

import androidx.compose.runtime.Immutable

/**
 * What the lyrics lookup for the current track established.
 *
 * [Absent] and [Unreachable] are kept apart on purpose, as [Instrumental] is kept apart from both.
 * "This song has no lyrics", "this recording is instrumental" and "I could not reach anywhere that
 * would know" are three different facts about three different situations, and the panel used to
 * answer all of them — plus "still asking" — with the same sentence, so a passenger in a tunnel was
 * told their music had no words.
 *
 * The distinction is also what makes a negative answer safe to cache: [Absent] and [Instrumental]
 * are evidence and get written to `track_lyrics.absentSince`, while [Unreachable] is the absence of
 * evidence and writes nothing.
 */
@Immutable
sealed interface LyricsState {
    /** The lookup is in flight. Distinct from having finished and found nothing. */
    data object Loading : LyricsState

    data class Present(val lyrics: LyricsData) : LyricsState

    /** Asked, and there are none to have. */
    data object Absent : LyricsState

    /** Asked, and the recording has no words to print. */
    data object Instrumental : LyricsState

    /** Could not ask. Says nothing about whether lyrics exist. */
    data object Unreachable : LyricsState
}
