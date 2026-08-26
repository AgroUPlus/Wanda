package com.wander.android.ui.screens.artist

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
class ArtistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val artist: String = savedStateHandle.get<String>("artist")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    /** The discography, newest first. Room-backed, so it is there before the network answers. */
    val albums: StateFlow<List<UnifiedAlbum>> = catalogRepository.artistAlbumsFlow(artist)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<UnifiedTrack>> = catalogRepository.artistTracksFlow(artist)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The backend's own id for this artist, taken from a track that credits them.
     *
     * This screen is keyed by *name* — it gathers everything by that artist across every source —
     * so there is no single id to share until at least one track has loaded. Null means the
     * action is not offered rather than offered and broken.
     */
    private fun artistTarget(): ShareTarget? {
        val track = tracks.value.firstOrNull { !it.artistId.isNullOrBlank() } ?: return null
        return ShareTarget(
            kind = ShareKind.ARTIST,
            source = track.source,
            id = track.artistId.orEmpty(),
            title = artist
        )
    }

    fun canShareArtist(): Boolean =
        artistTarget()?.let { shareRepository.canShare(it.source) } ?: false

    fun shareArtist() {
        val target = artistTarget() ?: return
        viewModelScope.launch { shareRepository.share(target) }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            catalogRepository.refreshArtist(artist)
            _isLoading.value = false
        }
    }

    fun image(): String? = catalogRepository.artistImage(albums.value, tracks.value)

    /** The artist's most played, which is what "play" on an artist page should mean. */
    fun playTop() = tracks.value.takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle() = tracks.value.takeIf { it.isNotEmpty() }
        ?.let { playerConnection.play(it.shuffled()) }

    fun play(index: Int) = playerConnection.play(tracks.value, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /** Radio for the artist as a whole, seeded from their most played. */
    fun startArtistRadio() {
        tracks.value.firstOrNull()?.let(::startRadio)
    }

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
}
