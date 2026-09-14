package com.wander.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

@Immutable
@Serializable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList()
)

@Immutable
enum class LyricsSyncType {
    NONE,
    LINE_SYNCED,
    WORD_SYNCED
}

@Immutable
@Serializable
data class LyricsData(
    val trackId: String,
    val isSynced: Boolean,
    val plainLyrics: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val source: String? = null
) {
    val syncType: LyricsSyncType
        get() = when {
            !isSynced || lines.isEmpty() -> LyricsSyncType.NONE
            lines.any { it.words.isNotEmpty() } -> LyricsSyncType.WORD_SYNCED
            else -> LyricsSyncType.LINE_SYNCED
        }
}

fun LyricsState.syncType(): LyricsSyncType = when (this) {
    is LyricsState.Present -> lyrics.syncType
    else -> LyricsSyncType.NONE
}

@Immutable
@Serializable
data class SmartMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val gradientColors: List<Long>,
    val seedType: String,
    val tracks: List<UnifiedTrack> = emptyList()
)
