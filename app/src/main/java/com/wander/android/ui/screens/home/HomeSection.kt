package com.wander.android.ui.screens.home

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.SmartMix
import com.wander.android.data.model.UnifiedTrack

/** How a shelf is laid out on Home. */
enum class HomeSectionStyle { TRACK_CAROUSEL, MIX_CAROUSEL, TRACK_LIST }

/**
 * One shelf on Home.
 *
 * Home is a list of these rather than a fixed set of named fields, so adding a shelf is adding an
 * entry in [HomeViewModel] and nothing else — and an empty shelf simply isn't emitted, instead of
 * rendering a heading with nothing under it.
 */
@Immutable
data class HomeSection(
    val id: String,
    val title: String,
    val style: HomeSectionStyle,
    val tracks: List<UnifiedTrack> = emptyList(),
    val mixes: List<SmartMix> = emptyList()
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && mixes.isEmpty()
}
