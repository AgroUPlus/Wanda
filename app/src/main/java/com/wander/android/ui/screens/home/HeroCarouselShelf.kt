package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack

/**
 * The "Carousel" layout: an M3 Expressive hero carousel with one oversized, centred card and
 * slivers of its neighbours either side. It is the look of Recently Played and Your Favorites, and
 * the one every shelf gets when it is set to Carousel.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroCarouselShelf(
    tracks: List<UnifiedTrack>,
    onPlay: (Int) -> Unit,
    onLongPress: (UnifiedTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val carouselState = rememberCarouselState { tracks.size }
    HorizontalCenteredHeroCarousel(
        state = carouselState,
        maxItemWidth = HeroItemWidth,
        itemSpacing = 8.dp,
        flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState),
        modifier = modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .padding(horizontal = 20.dp)
    ) { index ->
        val track = tracks[index]
        TrackCarouselCard(
            track = track,
            artworkSize = HeroItemWidth,
            showArtist = true,
            onPlay = { onPlay(index) },
            onLongPress = { onLongPress(track) }
        )
    }
}

private val HeroItemWidth = 300.dp
private val HeroHeight = 260.dp
