package com.wander.android.data.sources.scrobble

import com.wander.android.core.database.dao.PendingScrobble

/** How a batch went, in the terms the forwarding worker acts on. */
sealed interface ScrobbleOutcome {
    /** Sent — or refused for good, like a play Last.fm ignores; either way, not to be sent again. */
    data object Done : ScrobbleOutcome

    /** Worth trying again later: offline, the service down, or asked to slow down. */
    data class Retry(val reason: String) : ScrobbleOutcome

    /** The token or session no longer works; nothing will until the user connects again. */
    data class Unauthorized(val reason: String) : ScrobbleOutcome
}

/** What is being played right now, for the services' "now playing". */
data class NowPlaying(val title: String, val artist: String, val album: String?, val durationMs: Long)

/** The client name every service is told, so a listen says where it came from. */
internal const val SUBMISSION_CLIENT = "Wanda"

/**
 * When a play started, in Unix seconds, which is what both services ask for.
 *
 * `history.playedAt` is when the play *counted* — the moment playback passed the threshold in
 * `PlaybackService` (three quarters of the track, at most four minutes) — so the start is that
 * much earlier. Unknown length falls back to the thirty seconds the same rule uses.
 */
internal fun PendingScrobble.startedAtSeconds(): Long {
    val counted = if (durationMs > 0) minOf((durationMs * 0.75).toLong(), 240_000L).coerceAtLeast(15_000L) else 30_000L
    return (playedAt - counted) / 1000
}
