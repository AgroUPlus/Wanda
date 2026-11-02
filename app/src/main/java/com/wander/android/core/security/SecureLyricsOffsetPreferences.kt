package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * How far each track's synced lyrics are shifted from their timestamps, in milliseconds.
 *
 * Lyrics from LRCLIB or a server are often timed against a slightly different master — a radio
 * edit, a remaster with a longer intro — so they run a fixed amount early or late for that one
 * track. The listener corrects it once and it stays corrected. Positive means the lyrics come
 * later than their timestamps say, negative earlier.
 *
 * A preference rather than a column: the correction is the listener's taste about a track, not
 * library data, and keeping it here keeps it out of every schema migration. Only non-zero offsets
 * are kept, the most recently adjusted [MAX_TRACKS] of them, so the map stays small.
 */
internal class SecureLyricsOffsetPreferences(private val prefs: SharedPreferences) {

    private val _offsets = MutableStateFlow(read())
    val offsets: StateFlow<Map<String, Long>> = _offsets.asStateFlow()

    /** Moves [trackId]'s offset by [deltaMs], within [RANGE_MS]. */
    fun adjust(trackId: String, deltaMs: Long) {
        val next = LinkedHashMap(_offsets.value)
        val value = ((next.remove(trackId) ?: 0L) + deltaMs).coerceIn(RANGE_MS)
        if (value != 0L) next[trackId] = value
        while (next.size > MAX_TRACKS) next.remove(next.keys.first())
        prefs.edit { putString(KEY_LYRICS_OFFSETS, json.encodeToString(serializer, next)) }
        _offsets.value = next
    }

    fun reloadFlows() {
        _offsets.value = read()
    }

    private fun read(): Map<String, Long> {
        val stored = prefs.getString(KEY_LYRICS_OFFSETS, null) ?: return emptyMap()
        return try {
            json.decodeFromString(serializer, stored)
        } catch (e: SerializationException) {
            // Not ours to guess at: a value this version cannot read is dropped, not half-applied.
            prefs.edit { remove(KEY_LYRICS_OFFSETS) }
            emptyMap()
        }
    }

    companion object {
        /** Ten seconds either way covers any real mistiming; past that the lyrics are for another cut. */
        val RANGE_MS = -10_000L..10_000L
        private const val MAX_TRACKS = 500
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = MapSerializer(String.serializer(), Long.serializer())
    }
}
