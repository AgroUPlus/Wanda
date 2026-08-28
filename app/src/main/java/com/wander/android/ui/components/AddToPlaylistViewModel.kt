package com.wander.android.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.PlaylistWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State behind the add-to-playlist picker, shared by every screen that offers the action.
 *
 * Lives beside the composable rather than in a screen package because it belongs to the component,
 * not to any one screen — `HomeViewModel` and `LibraryViewModel` would otherwise carry an
 * identical copy of it each.
 */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlistWriter: PlaylistWriteRepository
) : ViewModel() {

    private val _target = MutableStateFlow<UnifiedTrack?>(null)
    val target: StateFlow<UnifiedTrack?> = _target.asStateFlow()

    private val _targetTracks = MutableStateFlow<List<UnifiedTrack>>(emptyList())

    private val _playlists = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    val playlists: StateFlow<List<UnifiedPlaylist>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun canAdd(track: UnifiedTrack): Boolean = playlistWriter.canWrite(track.source)

    fun open(track: UnifiedTrack) {
        _target.value = track
        _targetTracks.value = listOf(track)
        _playlists.value = emptyList()
        _isLoading.value = true
        viewModelScope.launch {
            _playlists.value = playlistWriter.writableTargets(track.source)
            _isLoading.value = false
        }
    }

    fun openForTracks(tracks: List<UnifiedTrack>, source: SourceType) {
        if (tracks.isEmpty()) return
        _target.value = tracks.first()
        _targetTracks.value = tracks
        _playlists.value = emptyList()
        _isLoading.value = true
        viewModelScope.launch {
            _playlists.value = playlistWriter.writableTargets(source)
            _isLoading.value = false
        }
    }

    fun dismiss() {
        _target.value = null
        _targetTracks.value = emptyList()
    }

    fun addToExisting(playlist: UnifiedPlaylist) {
        val trackIds = if (_targetTracks.value.isNotEmpty()) _targetTracks.value.map { it.id } else listOfNotNull(_target.value?.id)
        if (trackIds.isEmpty()) return
        dismiss()
        viewModelScope.launch { playlistWriter.addToPlaylist(playlist, trackIds) }
    }

    fun createWith(name: String) {
        val trackIds = if (_targetTracks.value.isNotEmpty()) _targetTracks.value.map { it.id } else listOfNotNull(_target.value?.id)
        val source = _target.value?.source ?: SourceType.LOCAL
        if (trackIds.isEmpty()) return
        dismiss()
        viewModelScope.launch { playlistWriter.createPlaylist(source, name, trackIds) }
    }
}
