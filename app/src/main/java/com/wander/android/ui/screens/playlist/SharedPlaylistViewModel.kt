package com.wander.android.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistEditor
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRunner
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * The shared side of the playlist screen: whether the playlist is shared through Agro, where its
 * copy stands, and the edits this account may make — remove and move, for a Wanda playlist too.
 *
 * Kept apart from [PlaylistViewModel], which loads and plays the tracks; both are keyed by the
 * same playlist id and read the same copy in Room, so their positions agree.
 */
@HiltViewModel
class SharedPlaylistViewModel @Inject constructor(
    private val shared: SharedPlaylistRepository,
    private val editor: SharedPlaylistEditor,
    private val runner: SharedPlaylistRunner,
    private val writes: PlaylistWriteRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    /** Null when the playlist is not shared. */
    val state: StateFlow<SharedPlaylistState?> = shared.observe(playlistId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val items: StateFlow<List<SharedPlaylistItemEntity>> = shared.observeItems(playlistId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _left = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires once the playlist has been unfollowed, for the screen to close. */
    val left: SharedFlow<Unit> = _left.asSharedFlow()

    init {
        // Opening a shared playlist is a reason to check it is current, push or no push.
        viewModelScope.launch { shared.agroIdFor(playlistId)?.let(runner::syncSoon) }
    }

    /**
     * Who added the track at [index], when that is worth showing: once others can edit, or in a
     * blend, where it names whose listening chose it.
     */
    fun addedBy(index: Int): String? =
        items.value.getOrNull(index)?.addedBy?.takeIf { state.value?.let { it.isCollaborative || it.isBlend } == true }

    fun isPending(index: Int): Boolean = items.value.getOrNull(index)?.isPending == true

    fun canRemove(playlist: UnifiedPlaylist, index: Int): Boolean {
        val current = state.value ?: return playlist.source == SourceType.LOCAL
        val item = items.value.getOrNull(index) ?: return false
        return editor.canRemove(current.effectiveRole, item)
    }

    fun canMove(playlist: UnifiedPlaylist): Boolean {
        val current = state.value ?: return playlist.source == SourceType.LOCAL
        return current.effectiveRole.canRearrange
    }

    /** [onDone] reloads the tracks: a shared copy announces its own change, a Wanda playlist does not. */
    fun remove(playlist: UnifiedPlaylist, index: Int, onDone: () -> Unit) {
        viewModelScope.launch { writes.removeFromPlaylist(playlist, index).onSuccess { onDone() } }
    }

    fun move(playlist: UnifiedPlaylist, from: Int, to: Int, onDone: () -> Unit) {
        viewModelScope.launch { writes.moveInPlaylist(playlist, from, to).onSuccess { onDone() } }
    }

    /** Tries again now, for a copy marked out of sync. */
    fun retry() {
        state.value?.agroId?.let(runner::syncSoon)
    }

    fun unfollow() {
        val current = state.value ?: return
        if (current.localPlaylistId != null) return
        viewModelScope.launch {
            shared.unfollow(current.agroId)
            _left.tryEmit(Unit)
        }
    }

}
