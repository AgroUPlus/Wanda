package com.wander.android.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pull-to-refresh, wearing the same face the app wears while it loads.
 *
 * Both refresh boxes took the default indicator, which is an arrow spinning into a circular
 * progress ring — a shape that appears nowhere else in Wanda. Every other wait in the app is the
 * Material 3 Expressive `LoadingIndicator`'s morphing shape, so pulling for new content looked
 * like a different application than opening one.
 *
 * `PullToRefreshDefaults.LoadingIndicator` is that same shape wired to the pull distance, so this
 * is a named wrapper rather than a reimplementation — its only job is that the two call sites
 * cannot drift apart, and that the reason is written down once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    modifier: Modifier = Modifier
) {
    PullToRefreshDefaults.LoadingIndicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier
    )
}
