package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.wander.android.ui.components.ExpressiveRefreshIndicator

/**
 * Pull-to-refresh for an album or playlist page, the way Home and Library already have it.
 *
 * The page runs edge to edge under the status bar, so the indicator is pushed down by [topInset]
 * rather than appearing behind it.
 */
@Composable
internal fun DetailRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topInset: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = refreshState,
        indicator = {
            ExpressiveRefreshIndicator(
                isRefreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset)
            )
        },
        modifier = Modifier.fillMaxSize(),
        content = content
    )
}
