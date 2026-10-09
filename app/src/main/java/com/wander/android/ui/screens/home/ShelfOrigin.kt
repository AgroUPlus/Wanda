package com.wander.android.ui.screens.home

import com.wander.android.data.repository.RecommendationRepository

/** Where a shelf's songs come from, for the customizer to tell them apart at a glance. */
internal enum class ShelfOrigin { LIBRARY, YOUTUBE_MUSIC, AGRO, FRIENDS }

internal fun shelfOrigin(id: String): ShelfOrigin = when {
    id.startsWith("ytm_") -> ShelfOrigin.YOUTUBE_MUSIC
    id == RecommendationRepository.PopularShelfId -> ShelfOrigin.AGRO
    id == ExtraShelf.FRIENDS.id -> ShelfOrigin.FRIENDS
    id == ExtraShelf.POPULAR_AGRO.id -> ShelfOrigin.AGRO
    id == ExtraShelf.YOUTUBE_MUSIC.id -> ShelfOrigin.YOUTUBE_MUSIC
    else -> ShelfOrigin.LIBRARY
}
