package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.SkeletonRow

/**
 * The placeholder for one activity card still on its way.
 *
 * Drawn at the foot of the feed while a longer page is being fetched, rather than a spinner: a
 * spinner says only that something is happening, while a row-shaped placeholder says what is
 * arriving and where it will land, so the list does not jump when it does.
 *
 * The leading size and shape match [FeedItemCard]'s avatar for exactly that reason.
 */
@Composable
internal fun FeedItemSkeleton(modifier: Modifier = Modifier) {
    SkeletonRow(
        leadingSize = FeedAvatarSize,
        leadingShape = MaterialTheme.shapes.medium,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/** How many arrive at once, matching the page the repository asks for closely enough to read. */
internal const val FEED_SKELETON_ROWS = 3

private val FeedAvatarSize = 40.dp
