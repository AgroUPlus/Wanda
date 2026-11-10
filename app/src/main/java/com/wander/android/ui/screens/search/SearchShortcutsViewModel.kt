package com.wander.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.HomeShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The slices of your own library the search start page offers to play in one tap. */
enum class SearchShortcut { RECENTLY_ADDED, MOST_PLAYED }

/**
 * Backs the start page's "Recently added" and "Most played" tiles.
 *
 * Both are Room reads through [HomeShelfRepository] — the same ones Home's shelves use — so they
 * cost nothing on the network and work offline. A shortcut with nothing behind it (a fresh
 * install, nothing played yet) is left out rather than shown as a tile that does nothing.
 */
@HiltViewModel
class SearchShortcutsViewModel @Inject constructor(
    private val shelves: HomeShelfRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    val tracks: StateFlow<Map<SearchShortcut, List<UnifiedTrack>>> = flow {
        emit(
            mapOf(
                SearchShortcut.RECENTLY_ADDED to shelves.getRecentlyAdded(QUEUE_LIMIT),
                SearchShortcut.MOST_PLAYED to shelves.getTopTracks(QUEUE_LIMIT)
            ).filterValues { it.isNotEmpty() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun play(shortcut: SearchShortcut) {
        tracks.value[shortcut]?.let { playerConnection.play(it) }
    }

    private companion object {
        const val QUEUE_LIMIT = 50
    }
}
