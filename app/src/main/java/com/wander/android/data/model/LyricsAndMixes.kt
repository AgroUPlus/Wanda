package com.wander.android.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null
)

@Immutable
@Serializable
data class LyricsData(
    val trackId: String,
    val isSynced: Boolean,
    val plainLyrics: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val source: String? = null
)

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
