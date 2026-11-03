package com.wander.android.ui.components.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.QueueInsert
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The batch actions every selectable list shares, and the swipe shortcuts on its rows. Screen-only
 * edits — removing from a playlist or from history — stay with the screen that owns them.
 */
@HiltViewModel
class TrackSelectionViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val musicRepository: MusicRepository
) : ViewModel() {

    /** From the database rather than the rows: a row's `isLiked` is as old as the list it came in. */
    val likedIds: StateFlow<Set<String>> = musicRepository.getLikedTrackIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun play(tracks: List<UnifiedTrack>) = playerConnection.play(tracks)

    /**
     * Queues [tracks] to play next and says so with [message], offering to undo it — but only
     * when there was a player to queue them on; with none the request has nowhere to go.
     */
    fun playNext(tracks: List<UnifiedTrack>, message: String) = confirm(playerConnection.playNext(tracks), message)

    fun addToQueue(tracks: List<UnifiedTrack>, message: String) = confirm(playerConnection.addToQueue(tracks), message)

    private fun confirm(insert: QueueInsert?, message: String) {
        if (insert != null) playerConnection.notify(message) { playerConnection.undoInsert(insert) }
    }

    /** Likes or unlikes every one of [tracks] not already that way; one at a time, as a tap would. */
    fun setLiked(tracks: List<UnifiedTrack>, liked: Boolean) {
        val current = likedIds.value
        val changing = tracks.filter { (it.id in current) != liked }.distinctBy { it.id }
        viewModelScope.launch { changing.forEach { musicRepository.toggleLike(it) } }
    }
}
