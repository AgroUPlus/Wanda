package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.ListenAlongResolver
import com.wander.android.data.sources.agro.AgroDrop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The inbox, read from Room and refreshed from the network behind it.
 *
 * The cache is what the screen shows, so it renders offline and renders instantly. [refresh] runs
 * on open because the socket is closed while the app is backgrounded — anything that arrived
 * overnight is learned here, not from a push.
 */
@HiltViewModel
internal class InboxViewModel @Inject constructor(
    private val drops: DropsRepository,
    private val resolver: ListenAlongResolver,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Why a drop could not be played. Nothing is said when one plays — the audio says it. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(drops.inbox, drops.sent, drops.unreadCount) { received, sent, unread ->
                InboxUiState(received = received, sent = sent, unread = unread, loading = false)
            }.collect { _state.value = it }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { drops.refresh() }
    }

    /**
     * Marks one read.
     *
     * The repository writes the local row first, so the list updates from Room without waiting —
     * a failed call is corrected by the next refresh rather than being reported, because there is
     * nothing the reader could do about it.
     */
    fun markRead(id: String) {
        viewModelScope.launch { drops.markRead(id) }
    }

    fun archive(id: String) {
        viewModelScope.launch { drops.archive(id) }
    }

    /**
     * Plays what somebody sent, and marks it read for having been opened.
     *
     * A drop carries a description rather than a reference — the sender may have been playing from
     * a backend this device has never heard of — so the track has to be *found* here. That is the
     * same problem listen-along solves, so it uses the same resolver: your own library first, then
     * YouTube Music, and a real match required rather than the top search hit.
     *
     * Goes through `PlayerConnection.play`, which means that while you are in a jam this proposes
     * the track to the room instead of playing it here. That is the app's rule for every play, and
     * quietly exempting drops from it would be the surprise.
     */
    fun play(drop: AgroDrop) {
        if (drop.isUnread) markRead(drop.id)
        _state.value = _state.value.copy(resolving = drop.id)
        viewModelScope.launch {
            val resolved = resolver.resolve(drop.trackTitle, drop.artistName)
            _state.value = _state.value.copy(resolving = null)
            if (resolved == null) {
                _messages.tryEmit("Couldn't find “${drop.trackTitle}” in your library or on YouTube Music.")
                return@launch
            }
            playerConnection.play(listOf(resolved.track))
        }
    }
}
