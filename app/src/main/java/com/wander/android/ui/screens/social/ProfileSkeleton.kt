package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionSkeleton
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.SkeletonBox
import com.wander.android.ui.components.SkeletonLine

/**
 * The profile page before it arrives.
 *
 * This screen used to say "Loading…" in body text in the top-left corner, which is the smallest
 * possible acknowledgement that anything is happening. Laid out as the real page instead — avatar,
 * name, handle, bio, the action button — so nothing moves when the content replaces it.
 */
@Composable
internal fun ProfileSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // [ProfileHero]'s own insets and gaps, box for box.
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
            SkeletonBox(modifier = Modifier.size(120.dp), shape = PersonShape)
            SkeletonLine(widthFraction = 0.5f, height = 44.dp, modifier = Modifier.padding(top = 20.dp))
            SkeletonLine(widthFraction = 0.3f, height = 14.dp, modifier = Modifier.padding(top = 8.dp))
            SkeletonLine(widthFraction = 0.85f, height = 14.dp, modifier = Modifier.padding(top = 12.dp))
            SkeletonLine(widthFraction = 0.65f, height = 14.dp, modifier = Modifier.padding(top = 6.dp))
        }
        // [ProfileActions]' row, at its 16 dp inset rather than the hero's 24.
        HeroActionRow(
            height = ProfileActionHeight + ProfileActionTopGap,
            modifier = Modifier.padding(top = ProfileActionTopGap)
        ) {
            HeroActionSkeleton(weight = 1f)
        }
    }
}
