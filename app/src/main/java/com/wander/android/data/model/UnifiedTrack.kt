package com.wander.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Locale

@Immutable
@Serializable
data class UnifiedTrack(
    val id: String,
    val source: SourceType,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitRateKbps: Int? = null,
    val format: String? = null,
    /**
     * A stream with no end: a livestream rather than a recording.
     *
     * Playback treats these differently in two ways that matter — the seek bar has nothing to
     * scrub over, and a load error must not be allowed to advance the queue, which is what made
     * live tracks look like they were being skipped.
     */
    val isLive: Boolean = false,
    val isLiked: Boolean = false,
    val isCached: Boolean = false,
    val isDownloaded: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long? = null,
    val extraData: Map<String, String> = emptyMap()
) {
    /**
     * Deliberately a `get()` accessor, not a stored `val`. Accessors have no backing field, so
     * kotlinx.serialization ignores them; a body `val` would be serialised into every `MediaItem`
     * extra as an optional field. The cost these used to carry in the scroll path was the
     * allocation inside them, not the accessor itself — so the body is allocation-light instead:
     * no `String.format`, no per-call `Set`.
     */
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (seconds < 10) "$minutes:0$seconds" else "$minutes:$seconds"
        }

    val audioQualityLabel: String?
        get() {
            val fmt = format?.uppercase(Locale.US)?.trim()
            return when {
                fmt in LosslessFormats -> fmt ?: "FLAC"
                bitRateKbps != null && bitRateKbps >= 320 -> "320 kbps"
                bitRateKbps != null && bitRateKbps > 0 -> "$bitRateKbps kbps"
                !fmt.isNullOrBlank() -> fmt
                else -> null
            }
        }
}

/**
 * Hoisted out of [UnifiedTrack.audioQualityLabel] so it is allocated once instead of on every
 * read — that accessor runs for every row on screen.
 *
 * Top-level rather than a companion object: `UnifiedTrack` is `@Serializable`, so the
 * serialization plugin puts the generated `serializer()` on its companion. Declaring that
 * companion `private` compiled cleanly but made the `Companion` field inaccessible to
 * `MediaItems.kt`, which serialises tracks into `MediaItem` extras — an `IllegalAccessError`
 * the moment anything was played.
 */
private val LosslessFormats = setOf("FLAC", "ALAC", "WAV", "AIFF")
