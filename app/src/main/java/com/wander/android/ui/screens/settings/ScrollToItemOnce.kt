package com.wander.android.ui.screens.settings

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Brings the list item keyed [key] to the top, once per visit, after the page first lays out.
 *
 * A `LazyColumn` only knows the keys of what it has composed, and a settings page is a list of
 * sections whose indices shift as rows come and go with what is signed in. So rather than an
 * index worked out in advance, this pages down a screenful at a time until the key is on screen,
 * then settles it at the top — a handful of steps on the longest page.
 *
 * Saved, so returning to the page (or rotating it) leaves wherever the reader has since scrolled.
 */
@Composable
internal fun ScrollToItemOnce(listState: LazyListState, key: String?) {
    var done by rememberSaveable(key) { mutableStateOf(key == null) }
    LaunchedEffect(key, done) {
        if (done) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        for (page in 0 until MAX_PAGES) {
            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            if (hit != null) {
                listState.animateScrollToItem(hit.index)
                done = true
                return@LaunchedEffect
            }
            if (!listState.canScrollForward) break
            listState.scrollBy(listState.layoutInfo.viewportSize.height.toFloat())
        }
        // Not on the page right now (a row shown only while signed in, say): the page is still
        // the right place, opened at the top.
        listState.scrollToItem(0)
        done = true
    }
}

private const val MAX_PAGES = 20
