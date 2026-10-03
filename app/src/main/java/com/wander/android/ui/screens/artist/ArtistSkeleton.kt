package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionSkeleton
import com.wander.android.ui.components.HeroPlayWeight
import com.wander.android.ui.components.HeroSecondaryRowHeight
import com.wander.android.ui.components.HeroTransportRowHeight
import com.wander.android.ui.components.ImmersiveHeroSkeleton
import com.wander.android.ui.components.SkeletonBox
import com.wander.android.ui.components.SkeletonLine
import com.wander.android.ui.components.SkeletonRow

/**
 * The artist page before it knows anything.
 *
 * Shaped like the real page rather than a spinner in the middle of it, so nothing moves when the
 * content lands on top. Previously this screen showed a centred indicator only while *completely*
 * empty and then snapped to whatever the library alone knew — which looked like a finished page
 * that was missing half the artist's work.
 *
 * The counts here are what a typical page has, not what this one will: three songs and three
 * records. Guessing high would leave the layout jumping upward as the real page turns out shorter.
 */
@Composable
internal fun ArtistSkeleton(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "hero") { HeroSkeleton() }

        item(key = "songs-title") {
            SkeletonLine(
                widthFraction = 0.32f,
                height = 20.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        items(3) { SkeletonRow(leadingSize = 44.dp, leadingShape = MaterialTheme.shapes.extraSmall) }

        item(key = "albums-title") {
            SkeletonLine(
                widthFraction = 0.26f,
                height = 20.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        item(key = "albums") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                repeat(3) {
                    Column {
                        SkeletonBox(
                            modifier = Modifier.size(132.dp),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(Modifier.height(8.dp))
                        SkeletonLine(widthFraction = 1f, height = 12.dp, modifier = Modifier.width(96.dp))
                    }
                }
            }
        }
    }
}

/**
 * Matches `ArtistHero` exactly — same portrait aspect, same two full-width weighted control rows —
 * so the hero is the one part of the page that does not move at all when the real data arrives.
 */
@Composable
private fun HeroSkeleton() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ImmersiveHeroSkeleton(aspect = PortraitAspect)
        HeroActionRow(height = HeroTransportRowHeight, modifier = Modifier.padding(top = 18.dp)) {
            HeroActionSkeleton(weight = 1f)
            HeroActionSkeleton(weight = HeroPlayWeight)
            HeroActionSkeleton(weight = 1f)
        }
        HeroActionRow(
            height = HeroSecondaryRowHeight,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
        ) {
            HeroActionSkeleton(weight = 1f)
            HeroActionSkeleton(weight = 1f)
        }
    }
}
