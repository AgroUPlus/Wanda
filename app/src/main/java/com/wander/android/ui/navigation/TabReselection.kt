package com.wander.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Taps on the tab you are already at the root of.
 *
 * The dock and the rail report them here; the tab's own screen decides what "home" means for it —
 * usually its list back to the top. A tap is an event, not state: nobody collecting means nobody
 * is on screen to react, so it is dropped rather than replayed onto a screen opened later.
 */
internal class TabReselection {
    /** The tab root the current screen was opened from; kept by `TrackTabOwner`. Not state: read on tap. */
    var owner: String? = null
    private val taps = MutableSharedFlow<TopLevelDestination>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun reselect(tab: TopLevelDestination) {
        taps.tryEmit(tab)
    }

    fun of(tab: TopLevelDestination): Flow<Unit> = taps.filter { it == tab }.map { }
}

/** The shell's [TabReselection], provided once by `WanderApp`. */
internal val LocalTabReselection = staticCompositionLocalOf<TabReselection> {
    error("LocalTabReselection is provided by WanderApp")
}

/**
 * Reselect taps meant for whatever surface reads this.
 *
 * A tab's destination provides its own taps; a surface inside it narrows them for what it holds —
 * see [InterceptReselect]. Anywhere outside a tab there are none.
 */
internal val LocalReselectSignal = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Gives [content] the reselect taps for [route], if [route] is a tab's root. */
@Composable
internal fun ProvideTabReselect(route: String, content: @Composable () -> Unit) {
    val tab = TopLevelDestination.entries.firstOrNull { it.route == route }
    if (tab == null) {
        content()
        return
    }
    val reselection = LocalTabReselection.current
    val signal = remember(reselection, tab) { reselection.of(tab) }
    CompositionLocalProvider(LocalReselectSignal provides signal, content)
}

/**
 * Runs [onReselect] for each reselect tap reaching here. A newer tap restarts it, so tapping
 * again mid-scroll does not queue a second scroll behind the first.
 */
@Composable
internal fun ReselectEffect(onReselect: suspend () -> Unit) {
    val signal = LocalReselectSignal.current
    val latest by rememberUpdatedState(onReselect)
    LaunchedEffect(signal) { signal.collectLatest { latest() } }
}

/**
 * Lets this surface take a reselect tap before [content] sees it — the way a `BackHandler` takes
 * back. [consume] returns true when it dealt with the tap itself (closed a search, say); only the
 * taps it leaves reach [content].
 */
@Composable
internal fun InterceptReselect(consume: () -> Boolean, content: @Composable () -> Unit) {
    val upstream = LocalReselectSignal.current
    val latest by rememberUpdatedState(consume)
    val downstream = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(upstream) {
        upstream.collect { if (!latest()) downstream.tryEmit(Unit) }
    }
    CompositionLocalProvider(LocalReselectSignal provides downstream, content)
}
