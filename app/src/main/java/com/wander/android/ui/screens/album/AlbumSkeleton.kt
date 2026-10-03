package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionSkeleton
import com.wander.android.ui.components.HeroPlayWeight
import com.wander.android.ui.components.HeroSecondaryRowHeight
import com.wander.android.ui.components.HeroTransportRowHeight
import com.wander.android.ui.components.ImmersiveHeroSkeleton
import com.wander.android.ui.components.SkeletonRow

/**
 * The album page before its tracklist arrives.
 *
 * Six rows, which is short for an album — deliberately. The list grows downward as the real
 * tracklist lands, and growing reads as loading finishing; shrinking from a guessed twelve would
 * read as tracks being taken away.
 */
@Composable
internal fun AlbumSkeleton(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(
        contentPadding = contentPadding,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "header") { HeaderSkeleton() }
        items(6) {
            SkeletonRow(leadingSize = 44.dp, leadingShape = MaterialTheme.shapes.extraSmall)
        }
    }
}

/**
 * Matches [AlbumHero] box for box, so nothing shifts when the real header replaces it. The second
 * row is drawn too: nearly every album and playlist can at least be shared.
 */
@Composable
private fun HeaderSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ImmersiveHeroSkeleton(aspect = CoverAspect)
        HeroActionRow(height = HeroTransportRowHeight, modifier = Modifier.padding(top = 18.dp)) {
            HeroActionSkeleton(weight = 1f)
            HeroActionSkeleton(weight = HeroPlayWeight)
        }
        HeroActionRow(
            height = HeroSecondaryRowHeight,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
        ) {
            HeroActionSkeleton(weight = 1f)
        }
    }
}
