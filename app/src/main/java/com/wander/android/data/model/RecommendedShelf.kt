package com.wander.android.data.model

import androidx.compose.runtime.Immutable

/**
 * A shelf of recommendations as a backend itself groups them — its own title, its own picks.
 *
 * Deliberately not flattened into one list of tracks. A recommendation feed's *shape* is part of
 * what it recommends: "Listen again" and "Mixed for you" are different suggestions about the same
 * library, and merging them throws away the only explanation the user gets for why a track is
 * being offered.
 */
@Immutable
data class RecommendedShelf(
    val id: String,
    val title: String,
    val tracks: List<UnifiedTrack> = emptyList()
)
