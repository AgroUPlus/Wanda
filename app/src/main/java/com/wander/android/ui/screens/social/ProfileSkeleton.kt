package com.wander.android.ui.screens.social

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.SkeletonBox
import com.wander.android.ui.components.SkeletonLine

/**
 * The profile page before it arrives.
 *
 * This screen used to say "Loading…" in body text in the top-left corner, which is the smallest
 * possible acknowledgement that anything is happening. Laid out as the real page instead — avatar,
 * name, handle, bio, two tiles — so nothing moves when the content replaces it.
 */
@Composable
internal fun ProfileSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        SkeletonBox(modifier = Modifier.size(112.dp), shape = CircleShape)
        Spacer(Modifier.height(2.dp))
        SkeletonLine(widthFraction = 0.5f, height = 22.dp)
        SkeletonLine(widthFraction = 0.3f, height = 14.dp)
        SkeletonLine(widthFraction = 0.85f, height = 14.dp)
        SkeletonLine(widthFraction = 0.65f, height = 14.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SkeletonBox(
                modifier = Modifier.weight(1f).height(72.dp),
                shape = MaterialTheme.shapes.medium
            )
            SkeletonBox(
                modifier = Modifier.weight(1f).height(72.dp),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}
