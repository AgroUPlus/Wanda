package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.ShapedActionSize
import com.wander.android.ui.components.SkeletonBox
import com.wander.android.ui.components.SkeletonLine
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

/** Matches [AlbumHero] box for box, so nothing shifts when the real header replaces it. */
@Composable
private fun HeaderSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
    ) {
        SkeletonBox(
            modifier = Modifier.size(CoverSize),
            shape = MaterialTheme.shapes.extraLarge
        )
        Spacer(Modifier.height(24.dp))
        SkeletonLine(widthFraction = 0.7f, height = 26.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonLine(widthFraction = 0.45f, height = 13.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 22.dp)
        ) {
            SkeletonBox(
                modifier = Modifier.size(ShapedActionSize),
                shape = MaterialTheme.shapes.small
            )
            SkeletonBox(modifier = Modifier.size(64.dp), shape = CircleShape)
            SkeletonBox(
                modifier = Modifier.size(ShapedActionSize),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}
