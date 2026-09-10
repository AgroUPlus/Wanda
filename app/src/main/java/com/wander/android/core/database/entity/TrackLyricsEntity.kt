package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted lyrics for a track, keeping both plain text and timestamped LRC formatting.
 *
 * Stored in its own table so `tracks` remains lean. Indexed by [LyricsFtsEntity] for
 * sub-millisecond full-text search across the entire library.
 */
@Entity(tableName = "track_lyrics")
data class TrackLyricsEntity(
    @PrimaryKey val trackId: String,
    val plainLyrics: String,
    val syncedLyrics: String? = null,
    val source: String,
    val syncedAt: Long = System.currentTimeMillis(),
    /**
     * When a lookup established this track has no lyrics to find, or null while it still might.
     *
     * A row carrying this holds no text: it records the *answer*, so that a track LRCLIB does not
     * have — an instrumental, a B-side, anything obscure — stops costing a request every time its
     * panel is opened. Only evidence of absence sets it. A failed request means the question went
     * unasked, and leaves this null so the next attempt still tries.
     */
    val absentSince: Long? = null,
    /**
     * Whether these lyrics arrived through the shared catalogue rather than being fetched here.
     *
     * Kept apart from [source], which names what originally supplied the text. The two are
     * different questions — a lyric that began at LRCLIB and reached this device through the fleet
     * is still an LRCLIB lyric — and folding them into one field meant every traded lyric lost its
     * origin at the first hop.
     */
    val viaCatalog: Boolean = false
)
