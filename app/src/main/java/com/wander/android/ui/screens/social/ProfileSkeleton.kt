package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        SkeletonBox(modifier = Modifier.size(120.dp), shape = PersonShape)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(widthFraction = 0.5f, height = 44.dp)
        SkeletonLine(widthFraction = 0.3f, height = 14.dp)
        SkeletonLine(widthFraction = 0.85f, height = 14.dp)
        SkeletonLine(widthFraction = 0.65f, height = 14.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape)
    }
}
