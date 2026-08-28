package com.wander.android.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistPageMerger
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class ArtistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val artist: String = savedStateHandle.get<String>("artist")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private val albums = catalogRepository.artistAlbumsFlow(artist)
    private val tracks = catalogRepository.artistTracksFlow(artist)
    private val details = MutableStateFlow<ArtistDetails?>(null)
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val expanded = MutableStateFlow<Map<String, List<UnifiedAlbum>>>(emptyMap())
    private val loadingShelf = MutableStateFlow<String?>(null)

    /**
     * One state, assembled from Room and the backend page together.
     *
     * The merge runs here rather than in the repository because it is a *view* decision — which
     * shelves this screen shows and in what order — and the repository has no business holding it.
     */
    val state: StateFlow<ArtistUiState> = combine(
        albums,
        tracks,
        details,
        combine(loading, refreshing, expanded, loadingShelf) { l, r, e, s -> Progress(l, r, e, s) }
    ) { albums, tracks, details, progress ->
        val page = ArtistPageMerger.merge(details, albums, tracks)
        ArtistUiState(
            artist = artist,
            page = page,
            heroImage = page.imageUrl ?: catalogRepository.artistImage(albums, tracks),
            albumCount = page.albums?.albums?.size ?: 0,
            trackCount = page.topSongs.size,
            // Content beats the flag: Room can answer before the network does, and a page that
            // already has records must not be replaced by a skeleton.
            isLoading = progress.loading && page.isEmpty,
            isRefreshing = progress.refreshing || (progress.loading && !page.isEmpty),
            canShare = artistTarget(tracks)?.let { shareRepository.canShare(it.source) } == true,
            expandedShelves = progress.expanded,
            loadingShelf = progress.loadingShelf
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArtistUiState(artist = artist)
    )

    private data class Progress(
        val loading: Boolean,
        val refreshing: Boolean,
        val expanded: Map<String, List<UnifiedAlbum>>,
        val loadingShelf: String?
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            catalogRepository.refreshArtist(artist)
            // *After* the search, not before: the artist's backend id comes off a track, and until
            // the search has persisted one there is nothing to ask the backend about. The loading
            // flag stays raised across both halves — clearing it here was what made the screen
            // render its library-only self and then reflow.
            val page = artistId()?.let { catalogRepository.artistDetails(it) }
            details.value = page
            // Remember the records the shelves named, so tapping one opens a page with a real
            // header instead of one reconstructed from whatever tracks happen to arrive.
            page?.sections?.filterIsInstance<ArtistAlbumSection>()
                ?.flatMap { it.albums }
                ?.let { catalogRepository.rememberAlbums(it) }
            loading.value = false
        }
    }

    /**
     * Fetches the whole of one album shelf.
     *
     * Only offered for a shelf that told us where its remainder lives; see
     * [ArtistAlbumSection.moreBrowseId]. A failure leaves the shelf as it was rather than emptying
     * it — the tiles already on screen are still true.
     */
    fun expandShelf(section: ArtistAlbumSection) {
        val browseId = section.moreBrowseId ?: return
        if (section.title in expanded.value || loadingShelf.value != null) return
        viewModelScope.launch {
            loadingShelf.value = section.title
            val all = catalogRepository.artistAlbumPage(browseId, section.moreParams)
            if (all.isNotEmpty()) {
                catalogRepository.rememberAlbums(all)
                expanded.value = expanded.value + (section.title to all)
            }
            loadingShelf.value = null
        }
    }

    private fun artistTarget(from: List<UnifiedTrack>): ShareTarget? {
        val track = from.firstOrNull { !it.artistId.isNullOrBlank() } ?: return null
        return ShareTarget(
            kind = ShareKind.ARTIST,
            source = track.source,
            id = track.artistId.orEmpty(),
            title = artist
        )
    }

    private fun artistId(): String? = state.value.page.topSongs
        .firstNotNullOfOrNull { it.artistId?.takeIf(String::isNotBlank) }

    fun shareArtist() {
        val target = artistTarget(state.value.page.topSongs) ?: return
        viewModelScope.launch { shareRepository.share(target) }
    }

    private fun topSongs() = state.value.page.topSongs

    fun playTop() = topSongs().takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle() = topSongs().takeIf { it.isNotEmpty() }
        ?.let { playerConnection.play(it.shuffled()) }

    fun play(index: Int) = playerConnection.play(topSongs(), index)

    /**
     * Plays one track on its own — for a song that only appears on a shelf, where an index into
     * the merged song list means nothing.
     */
    fun playOne(track: UnifiedTrack) = playerConnection.play(listOf(track))

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /** Radio for the artist as a whole, seeded from their most played. */
    fun startArtistRadio() {
        topSongs().firstOrNull()?.let(::startRadio)
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
