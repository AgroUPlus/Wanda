package com.wander.android.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistPageMerger
import com.wander.android.data.repository.ArtistSubscriptionRepository
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ArtistViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val shareRepository: ShareRepository,
    private val subscriptions: ArtistSubscriptionRepository,
    private val loader: ArtistCatalogLoader,
    private val playback: ArtistPlaybackCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val artist: String = savedStateHandle.get<String>("artist")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private val routeArtistId: String? = savedStateHandle.get<String>("artistId")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.isNotBlank() }

    private val pageArtistId: StateFlow<String?> = loader.details
        .map { routeArtistId ?: it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, routeArtistId)

    private val albums = pageArtistId.flatMapLatest { id ->
        catalogRepository.artistAlbumsFlow(artist, id)
    }
    private val tracks = pageArtistId.flatMapLatest { id ->
        catalogRepository.artistTracksFlow(artist, id)
    }

    private val following = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            following.value = subscriptions.isSubscribed(artist)
        }
        loader.initLoader(artist, routeArtistId, viewModelScope)
    }

    val state: StateFlow<ArtistUiState> = combine(
        albums,
        tracks,
        loader.details,
        combine(loader.loading, loader.refreshing, loader.expanded, loader.loadingShelf, loader.hasCache) { l, r, e, s, c ->
            Progress(l as Boolean, r as Boolean, e as Map<String, List<UnifiedAlbum>>, s as String?, c as Boolean)
        },
        following
    ) { albums, tracks, details, progress, following ->
        val page = ArtistPageMerger.merge(details, albums, tracks)
        ArtistUiState(
            artist = artist,
            page = page,
            heroImage = page.imageUrl,
            albumCount = page.albums?.albums?.size ?: 0,
            trackCount = page.topSongs.size,
            isFollowing = following,
            isLoading = progress.loading && !progress.cached,
            isRefreshing = progress.refreshing || (progress.loading && progress.cached),
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
        val loadingShelf: String?,
        val cached: Boolean
    )

    fun refresh(skipSearchIfFresh: Boolean = false) {
        loader.refresh(artist, topSongs(), skipSearchIfFresh, viewModelScope)
    }

    fun expandShelf(section: ArtistAlbumSection) {
        loader.expandShelf(section, artist, viewModelScope)
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

    fun toggleFollow() {
        val wasFollowing = following.value ?: return
        following.value = !wasFollowing
        viewModelScope.launch {
            val ok = if (wasFollowing) {
                subscriptions.subscribed()
                    .firstOrNull {
                        it.normName.equals(artist.trim(), ignoreCase = true) ||
                            it.displayName.equals(artist.trim(), ignoreCase = true)
                    }
                    ?.let { subscriptions.unsubscribe(it) } ?: false
            } else {
                subscriptions.subscribe(artist, channelId = ytChannelId())
            }
            if (!ok) following.value = wasFollowing
        }
    }

    private fun ytChannelId(): String? = pageArtistId.value
        ?.takeIf { it.startsWith("ytm:") }
        ?.removePrefix("ytm:")

    fun shareArtist() {
        val target = artistTarget(state.value.page.topSongs) ?: return
        viewModelScope.launch { shareRepository.share(target) }
    }

    private fun topSongs() = state.value.page.topSongs

    fun playTop() = playback.playTop(topSongs())
    fun shuffle() = playback.shuffle(topSongs())
    fun play(index: Int) = playback.play(topSongs(), index)
    fun playOne(track: UnifiedTrack) = playback.playOne(track)
    fun playNext(track: UnifiedTrack) = playback.playNext(track)
    fun addToQueue(track: UnifiedTrack) = playback.addToQueue(track)

    fun startArtistRadio() {
        topSongs().firstOrNull()?.let(::startRadio)
    }

    fun startRadio(track: UnifiedTrack) = playback.startRadio(track, viewModelScope)
    fun toggleLike(track: UnifiedTrack) = playback.toggleLike(track, viewModelScope)
    fun canShare(track: UnifiedTrack) = playback.canShare(track)
    fun share(track: UnifiedTrack) = playback.share(track, viewModelScope)

    fun playAlbum(album: UnifiedAlbum) = playback.playAlbum(album, viewModelScope)
    fun playAlbumNext(album: UnifiedAlbum) = playback.playAlbumNext(album, viewModelScope)
    fun addAlbumToQueue(album: UnifiedAlbum) = playback.addAlbumToQueue(album, viewModelScope)
    fun canShareAlbum(album: UnifiedAlbum): Boolean = playback.canShareAlbum(album)
    fun shareAlbum(album: UnifiedAlbum) = playback.shareAlbum(album, viewModelScope)
    fun getAlbumTracks(album: UnifiedAlbum, onTracks: (List<UnifiedTrack>) -> Unit) =
        playback.getAlbumTracks(album, viewModelScope, onTracks)
}
