package com.wander.android.ui.screens.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.core.database.entity.ArtistEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    /**
     * The id the caller pointed at, when they knew it.
     *
     * Believed over anything derived from the name. `artistId()` below picks the first id off
     * whatever Room returned for this *name*, and Room folds case — so on a page for "yuri" it
     * could return the other Yuri's id, fetch her discography, and then filter the page down to
     * exactly the wrong artist's songs. A caller who tapped a track knows which of them they meant.
     */
    private val routeArtistId: String? = savedStateHandle.get<String>("artistId")
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.isNotBlank() }

    /**
     * The best identity known right now, without consulting a flow.
     *
     * [artistId] below reads `state.value`, and `state` only holds anything once Room has emitted
     * into it — which used to happen incidentally, during the seconds the cross-source search was
     * running. Skipping that search for a cached artist removed the delay and with it the emission,
     * so the id came back null, the page was never fetched, and a revisit showed top songs and no
     * bio, albums or shelves at all. Held as a plain field so it is true immediately.
     */
    private var knownArtistId: String? = null

    /** Whether a cached row already existed when this page opened. See the cache write below. */
    private var cachedArtist: ArtistEntity? = null

    private val details = MutableStateFlow<ArtistDetails?>(null)

    /**
     * Whoever's page this is, once a backend has said so.
     *
     * The route carries only a name, so identity is not known until the artist page loads — and
     * two different artists can share a name. Room's flows are re-subscribed when it arrives so
     * that somebody else's songs stop being listed under this one. See [ArtistIdentity].
     */
    private val pageArtistId: StateFlow<String?> = details
        .map { routeArtistId ?: it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, routeArtistId)

    private val albums = pageArtistId.flatMapLatest { id ->
        catalogRepository.artistAlbumsFlow(artist, id)
    }
    private val tracks = pageArtistId.flatMapLatest { id ->
        catalogRepository.artistTracksFlow(artist, id)
    }
    private val loading = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val expanded = MutableStateFlow<Map<String, List<UnifiedAlbum>>>(emptyMap())
    private val loadingShelf = MutableStateFlow<String?>(null)

    /**
     * True once we have shown this artist before.
     *
     * Drives the skeleton, and only the skeleton. A first visit has nothing trustworthy to draw —
     * Room may hold another artist of the same name, and until the backend says who this is there
     * is no way to tell — so it waits. A return visit already knows the identity, so the songs and
     * records in Room are known to be theirs and go up immediately, with the refresh happening
     * underneath rather than in front.
     */
    private val hasCache = MutableStateFlow(false)


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
        combine(loading, refreshing, expanded, loadingShelf, hasCache) { l, r, e, s, c ->
            Progress(l as Boolean, r as Boolean, e as Map<String, List<UnifiedAlbum>>, s as String?, c as Boolean)
        }
    ) { albums, tracks, details, progress ->
        val page = ArtistPageMerger.merge(details, albums, tracks)
        ArtistUiState(
            artist = artist,
            page = page,
            heroImage = page.imageUrl ?: catalogRepository.artistImage(albums, tracks),
            albumCount = page.albums?.albums?.size ?: 0,
            trackCount = page.topSongs.size,
            // The skeleton stays up until the backend has actually answered.
            //
            // This used to be `progress.loading && page.isEmpty` — content beat the flag — so the
            // moment Room returned anything name-matched the page drew itself and then rearranged
            // under the reader as the real shelves, portrait and song list landed. Worse, before
            // the identity filter above had an id to work with, that first paint could be another
            // artist's material entirely. One paint, once the answer is in.
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


    init {
        knownArtistId = routeArtistId
        viewModelScope.launch {
            val cached = catalogRepository.cachedArtist(artist).also { cachedArtist = it }
            if (cached != null) {
                hasCache.value = true
                // The cached id is what lets the page fetch below run at once, instead of waiting
                // for Room to emit something to infer it from.
                if (knownArtistId == null) {
                    knownArtistId = cached.artistId?.takeIf(String::isNotBlank)
                }
                // Seeded as a page with no shelves: enough for the header to be right and for the
                // identity filter to start working, while the shelves themselves arrive from the
                // fetch below. The merger fills the rest from Room.
                details.value = ArtistDetails(
                    id = cached.artistId.orEmpty(),
                    name = cached.name,
                    imageUrl = cached.imageUrl,
                    bio = cached.bio
                )
            }
            refresh(skipSearchIfFresh = cached != null && catalogRepository.isFresh(cached))
        }
    }

    fun refresh(skipSearchIfFresh: Boolean = false) {
        viewModelScope.launch {
            loading.value = true
            // `finally`, because the skeleton is driven by this flag alone on a first visit. It
            // used to be `loading && page.isEmpty`, so a throw here merely left a stale flag
            // behind whatever Room could show; now it would strand the screen on a skeleton.
            try {
                // The cross-source search is the expensive half of opening an artist — every
                // configured backend, asked for one name. Skipped while the cached page is recent,
                // because a discography does not change between two visits minutes apart.
                if (!skipSearchIfFresh) catalogRepository.refreshArtist(artist)
                // *After* the search, not before: the artist's backend id comes off a track, and
                // until the search has persisted one there is nothing to ask the backend about.
                val id = knownArtistId ?: artistId()?.also { knownArtistId = it }
                val page = id?.let { catalogRepository.artistDetails(it) }
                if (page != null) {
                    details.value = page
                    knownArtistId = page.id
                }
                // Only overwrite the cache with a real answer. Writing a null over a good row would
                // throw away the id that makes the *next* visit work, which is the point of caching.
                if (page != null || cachedArtist == null) {
                    catalogRepository.cacheArtist(artist, page)
                }
                hasCache.value = true
                // Remember the records the shelves named, so tapping one opens a page with a real
                // header instead of one reconstructed from whatever tracks happen to arrive.
                page?.sections?.filterIsInstance<ArtistAlbumSection>()
                    ?.flatMap { it.albums }
                    ?.let { catalogRepository.rememberAlbums(it) }
            } finally {
                loading.value = false
            }
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
            val all = catalogRepository.artistAlbumPage(browseId, section.moreParams, artist)
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

    /**
     * A guess at who this page is about, from what Room holds under this name.
     *
     * Only consulted when the caller supplied no id — arriving from a deep link, or from a place
     * that genuinely only knows a name. It cannot tell two same-named artists apart, which is
     * exactly why [routeArtistId] wins whenever it exists.
     */
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
