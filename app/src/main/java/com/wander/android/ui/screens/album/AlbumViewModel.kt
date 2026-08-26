package com.wander.android.ui.screens.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>("albumId")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    /** Room-backed, so an album opened before renders instantly and offline. */
    val tracks: StateFlow<List<UnifiedTrack>> = catalogRepository.albumTracksFlow(albumId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _album = MutableStateFlow<UnifiedAlbum?>(null)
    val album: StateFlow<UnifiedAlbum?> = _album.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _album.value = catalogRepository.album(albumId)
            // Fills in tracks Room has not seen. The flow above picks them up on its own.
            catalogRepository.refreshAlbum(albumId)
            _album.value = catalogRepository.album(albumId) ?: _album.value
            _isLoading.value = false
        }
    }

    fun playAll() = tracks.value.takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle() = tracks.value.takeIf { it.isNotEmpty() }
        ?.let { playerConnection.play(it.shuffled()) }

    fun play(index: Int) = playerConnection.play(tracks.value, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(track: UnifiedTrack) {
        viewModelScope.launch {
            playerConnection.play(listOf(track))
            val radio = musicRepository.generateRadio(track)
            if (radio.isNotEmpty()) playerConnection.addToQueue(radio)
        }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    /** Whether this record's backend can publish a link for the album itself, not just a track. */
    fun canShareAlbum(): Boolean =
        _album.value?.let { shareRepository.canShare(it.source) } ?: false

    fun shareAlbum() {
        val album = _album.value ?: return
        viewModelScope.launch {
            shareRepository.share(
                ShareTarget(
                    kind = ShareKind.ALBUM,
                    source = album.source,
                    id = album.id,
                    title = album.title,
                    subtitle = album.artist
                )
            )
        }
    }
}
