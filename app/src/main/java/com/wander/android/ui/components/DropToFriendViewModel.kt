package com.wander.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sending one track to one friend.
 *
 * The friend list comes from the Room-backed graph, so the picker has something to show the instant
 * it opens and works with the network down — the send itself will fail offline, which is reported
 * rather than queued. A drop is a small deliberate act; silently holding one to send later would
 * mean the recipient getting it at a moment neither person chose.
 */
@HiltViewModel
internal class DropToFriendViewModel @Inject constructor(
    private val drops: DropsRepository,
    social: SocialRepository
) : ViewModel() {

    val friends: StateFlow<List<AgroProfile>> = social.friends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /**
     * Hands [track] to [to].
     *
     * [onDone] is given the failure, or null on success, so the caller can say what happened. The
     * server refuses a stranger with the same message it uses for an account that does not exist,
     * so there is nothing more specific worth reporting than that it did not go.
     */
    fun send(
        to: String,
        track: UnifiedTrack,
        note: String,
        onDone: (Throwable?) -> Unit
    ) {
        if (_sending.value) return
        _sending.value = true
        viewModelScope.launch {
            val result = drops.drop(
                to = to,
                trackTitle = track.title,
                artistName = track.artist,
                albumName = track.album,
                artworkUrl = track.artworkUrl,
                // Only ever the id this device already publishes for handoff. There is no hash for
                // a streamed track, and inventing one would be a reference that resolves nowhere.
                trackUri = track.id,
                note = note.takeIf { it.isNotBlank() }
            )
            _sending.value = false
            onDone(result.exceptionOrNull())
        }
    }
}
