package com.wander.android.ui.screens.home.customize

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.wander.android.data.repository.RecommendationRepository
import com.wander.android.ui.screens.home.ExtraShelf
import com.wander.android.ui.screens.home.SectionBecause
import com.wander.android.ui.screens.home.SectionContinueListening
import com.wander.android.ui.screens.home.SectionDiscover
import com.wander.android.ui.screens.home.SectionLiked
import com.wander.android.ui.screens.home.SectionMixes
import com.wander.android.ui.screens.home.SectionOnRepeat
import com.wander.android.ui.screens.home.SectionRecentlyPlayed

/** A glyph for each shelf, so a list of shelves can be told apart at a glance. */
internal fun ExtraShelf.icon(): ImageVector = when (this) {
    ExtraShelf.REDISCOVER -> Icons.Rounded.History
    ExtraShelf.HEAVY_ROTATION -> Icons.Rounded.LocalFireDepartment
    ExtraShelf.FRESH -> Icons.Rounded.NewReleases
    ExtraShelf.LATE_NIGHT -> Icons.Rounded.Bedtime
    ExtraShelf.RANDOM -> Icons.Rounded.Shuffle
    ExtraShelf.FRIENDS -> Icons.Rounded.People
    ExtraShelf.POPULAR_AGRO -> Icons.Rounded.Public
    ExtraShelf.YOUTUBE_MUSIC -> Icons.Rounded.PlayCircle
}

/** The glyph for a default shelf, by id. Shelves from YouTube Music's feed share one. */
internal fun shelfIcon(id: String): ImageVector = when {
    id == SectionOnRepeat -> Icons.Rounded.Repeat
    id == SectionRecentlyPlayed -> Icons.Rounded.History
    id == SectionContinueListening -> Icons.Rounded.Podcasts
    id == SectionMixes -> Icons.Rounded.AutoAwesome
    id == SectionLiked -> Icons.Rounded.Favorite
    id == SectionDiscover -> Icons.Rounded.Explore
    id == SectionBecause -> Icons.Rounded.Lightbulb
    id == RecommendationRepository.PopularShelfId -> Icons.Rounded.Public
    id.startsWith("ytm_") -> Icons.Rounded.PlayCircle
    else -> Icons.Rounded.History
}
