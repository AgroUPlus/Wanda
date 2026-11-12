package com.wander.android.ui.screens.library

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

/** The slices of your own library the Library screen offers to play in one tap. */
enum class LibraryShortcut { RECENTLY_ADDED, MOST_PLAYED }

/**
 * Backs the Library's "Recently added" and "Most played" tiles, beside Liked and Offline.
 *
 * Both are Room reads through [HomeShelfRepository] — the same ones Home's shelves use — so they
 * cost nothing on the network and work offline. A shortcut with nothing behind it (a fresh
 * install, nothing played yet) is left out rather than shown as a tile that does nothing.
 */
@HiltViewModel
class LibraryShortcutsViewModel @Inject constructor(
    private val shelves: HomeShelfRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    val tracks: StateFlow<Map<LibraryShortcut, List<UnifiedTrack>>> = flow {
        emit(
            mapOf(
                LibraryShortcut.RECENTLY_ADDED to shelves.getRecentlyAdded(QUEUE_LIMIT),
                LibraryShortcut.MOST_PLAYED to shelves.getTopTracks(QUEUE_LIMIT)
            ).filterValues { it.isNotEmpty() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun play(shortcut: LibraryShortcut) {
        tracks.value[shortcut]?.let { playerConnection.play(it) }
    }

    private companion object {
        const val QUEUE_LIMIT = 50
    }
}
