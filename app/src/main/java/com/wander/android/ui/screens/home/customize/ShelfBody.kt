package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeShelfStates
import com.wander.android.ui.screens.home.HomeViewModel
import com.wander.android.ui.screens.home.homeSection

/**
 * A shelf drawn by the very code Home draws it with, so the customizer shows each shelf exactly as
 * it is — same layout, same artwork, same horizontal scrolling.
 *
 * `homeSection` emits lazy-list items, one per part of the shelf. They are collected here and laid
 * out in a plain column, one editable block per shelf, with the same 16 dp between parts that
 * Home's list puts between items.
 */
@Composable
internal fun ShelfBody(
    section: HomeSection,
    viewModel: HomeViewModel,
    states: HomeShelfStates,
    modifier: Modifier = Modifier
) {
    val parts = remember(section) {
        InlineShelfScope().also { it.homeSection(section, viewModel, states) {} }.parts
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        parts.forEachIndexed { index, part -> key(index) { with(InlineItemScope) { part() } } }
    }
}

private class InlineShelfScope : LazyListScope {
    val parts = mutableListOf<@Composable LazyItemScope.() -> Unit>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        parts += content
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        repeat(count) { index -> parts += { itemContent(index) } }
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        parts += { content(0) }
    }
}

/** A lazy item's scope with nothing lazy about it: sizes fill the column, and nothing animates. */
private object InlineItemScope : LazyItemScope {
    override fun Modifier.fillParentMaxSize(fraction: Float) = fillMaxSize(fraction)
    override fun Modifier.fillParentMaxWidth(fraction: Float) = fillMaxWidth(fraction)
    override fun Modifier.fillParentMaxHeight(fraction: Float) = fillMaxHeight(fraction)
    override fun Modifier.animateItem(
        fadeInSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>?,
        placementSpec: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>?,
        fadeOutSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>?
    ) = this
}
