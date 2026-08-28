package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.ShapedActionSize
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
        items(3) { SkeletonRow(leadingSize = 44.dp, leadingShape = RoundedCornerShape(10.dp)) }

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
 * Matches `ArtistHero` exactly — same card, same portrait size, same control row — so the hero is
 * the one part of the page that does not move at all when the real data arrives.
 */
@Composable
private fun HeroSkeleton() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(modifier = Modifier.size(PortraitSize), shape = CircleShape)
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                ) {
                    SkeletonLine(widthFraction = 0.78f, height = 22.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonLine(widthFraction = 0.5f, height = 13.dp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                repeat(2) {
                    SkeletonBox(
                        modifier = Modifier.size(ShapedActionSize),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {}
                SkeletonBox(modifier = Modifier.size(64.dp), shape = CircleShape)
            }
        }
    }
}
