package com.wander.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * [ImmersiveHero] before its page has loaded: the picture's box, and the sheet rising over it with
 * an overline, a title and a subtitle — so nothing moves when the real hero replaces it.
 */
@Composable
internal fun ImmersiveHeroSkeleton(aspect: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
            shape = RectangleShape
        )
        Surface(
            shape = HeroSheetShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().riseBy(HeroSheetOverlap)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                SkeletonLine(widthFraction = 0.2f, height = 13.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonLine(widthFraction = 0.7f, height = 30.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonLine(widthFraction = 0.45f, height = 15.dp)
            }
        }
    }
}
