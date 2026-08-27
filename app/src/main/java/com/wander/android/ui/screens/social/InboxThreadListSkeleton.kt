package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.listInset

/**
 * What the inbox shows on a cold start.
 *
 * It used to show nothing — the list rendered empty and the "no conversations yet" copy was
 * suppressed until loading finished, so the screen was blank for as long as the first read took.
 * Blank reads as broken.
 *
 * Only on the *first* load, and only with nothing cached: Room answers immediately after that, and
 * placeholders flashing over content that is already on screen would be worse than the blank was.
 */
@Composable
internal fun InboxThreadListSkeleton(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.listInset())
    ) {
        // Enough to fill the fold and no more. A placeholder below the fold is work nobody sees.
        repeat(PLACEHOLDER_ROWS) { SkeletonRow() }
    }
}

private const val PLACEHOLDER_ROWS = 7
