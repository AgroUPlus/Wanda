package com.wander.android.ui.screens.home.layout

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * How many songs were started from each Home shelf since counting began at [since] (epoch
 * millis, 0 before the first play). Only counts are kept: no songs, no times, nothing about what
 * was played.
 */
@Serializable
data class ShelfUsage(val since: Long = 0L, val plays: Map<String, Int> = emptyMap()) {

    fun playsFrom(shelfId: String): Int = plays[shelfId] ?: 0

    fun recorded(shelfId: String, now: Long): ShelfUsage =
        copy(since = if (since == 0L) now else since, plays = plays + (shelfId to playsFrom(shelfId) + 1))
}

internal object ShelfUsageCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(usage: ShelfUsage): String = json.encodeToString(ShelfUsage.serializer(), usage)

    /** An unreadable value counts as no history rather than failing Home. */
    fun decode(raw: String?): ShelfUsage {
        if (raw.isNullOrBlank()) return ShelfUsage()
        return try {
            json.decodeFromString(ShelfUsage.serializer(), raw)
        } catch (_: SerializationException) {
            ShelfUsage()
        } catch (_: IllegalArgumentException) {
            ShelfUsage()
        }
    }
}
