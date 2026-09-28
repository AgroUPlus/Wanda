package com.wander.android.ui.screens.artist

import com.wander.android.core.database.entity.ArtistEntity
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistFetch
import com.wander.android.data.repository.ArtistIdentity
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.sources.musicbrainz.MUSICBRAINZ_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads, caches, and refreshes artist details and album shelves from [CatalogRepository].
 */
internal class ArtistCatalogLoader @Inject constructor(
    private val catalogRepository: CatalogRepository
) {
    val details = MutableStateFlow<ArtistDetails?>(null)
    val loading = MutableStateFlow(true)
    val refreshing = MutableStateFlow(false)
    val expanded = MutableStateFlow<Map<String, List<UnifiedAlbum>>>(emptyMap())
    val loadingShelf = MutableStateFlow<String?>(null)
    val hasCache = MutableStateFlow(false)

    /**
     * True when the last refresh had an id to fetch and the fetch genuinely failed (network,
     * parsing) — never true for "no id was resolvable" or "the page was about someone else",
     * both of which are an artist with no backend page, not an error. See [ArtistFetch].
     */
    val fetchFailed = MutableStateFlow(false)

    private var knownArtistId: String? = null
    private var cachedArtist: ArtistEntity? = null

    fun initLoader(
        artist: String,
        routeArtistId: String?,
        scope: CoroutineScope
    ) {
        knownArtistId = routeArtistId
        scope.launch {
            val cached = catalogRepository.cachedArtist(artist).also { cachedArtist = it }
            if (cached != null) {
                hasCache.value = true
                details.value = ArtistDetails(
                    id = cached.artistId.orEmpty(),
                    name = cached.name,
                    imageUrl = cached.imageUrl,
                    bio = cached.bio
                )
            }
            refresh(
                artist = artist,
                topSongs = emptyList(),
                skipSearchIfFresh = cached != null && catalogRepository.isFresh(cached),
                scope = scope
            )
        }
    }

    fun refresh(
        artist: String,
        topSongs: List<UnifiedTrack>,
        skipSearchIfFresh: Boolean = false,
        scope: CoroutineScope
    ) {
        scope.launch {
            loading.value = true
            try {
                if (!skipSearchIfFresh) catalogRepository.refreshArtist(artist)
                val idWasGiven = knownArtistId != null
                val id = knownArtistId ?: findArtistId(artist, topSongs)?.also { knownArtistId = it }
                // No id from any track at all — most often a local-only artist, which never had a
                // page to fetch in the first place. MusicBrainz is asked by name as a last resort,
                // rather than giving up the way this always used to: see `MusicBrainzArtistFallback`.
                // An id already carrying that prefix (a page a *previous* refresh got from it) has
                // to go back the same way: no real source's id prefix is `mbid:`, so routing it
                // through `artistDetails` would find nothing and blank out what MusicBrainz found.
                val fetch = if (id != null && !id.startsWith(MUSICBRAINZ_PREFIX)) {
                    catalogRepository.artistDetails(id, if (idWasGiven) null else artist)
                } else {
                    catalogRepository.musicBrainzArtistFallback(artist)
                }
                fetchFailed.value = fetch is ArtistFetch.Failed
                val page = (fetch as? ArtistFetch.Found)?.page
                if (page != null) {
                    details.value = page
                    knownArtistId = page.id
                }
                if (page != null || cachedArtist == null) {
                    catalogRepository.cacheArtist(artist, page)
                }
                hasCache.value = true
                page?.sections?.filterIsInstance<ArtistAlbumSection>()
                    ?.flatMap { it.albums }
                    ?.let { catalogRepository.rememberAlbums(it) }
            } finally {
                loading.value = false
            }
        }
    }

    fun expandShelf(
        section: ArtistAlbumSection,
        artist: String,
        scope: CoroutineScope
    ) {
        val browseId = section.moreBrowseId ?: return
        if (section.title in expanded.value || loadingShelf.value != null) return
        scope.launch {
            loadingShelf.value = section.title
            val all = catalogRepository.artistAlbumPage(browseId, section.moreParams, artist)
            if (all.isNotEmpty()) {
                catalogRepository.rememberAlbums(all)
                expanded.value = expanded.value + (section.title to all)
            }
            loadingShelf.value = null
        }
    }

    /**
     * The id worth fetching a page for, among every same-named track's own id.
     *
     * Prefers an id from a source that actually publishes artist pages
     * ([CatalogRepository.artistCapableIdPrefixes]) over whichever same-named track happened to
     * come first: a track's own backend can be one with no artist page at all (local files, most
     * of the time), and picking its id meant [CatalogRepository.artistDetails] had nothing that
     * would ever match it — the fetch was never going to succeed, for a reason invisible from here.
     */
    private fun findArtistId(artistName: String, songs: List<UnifiedTrack>): String? {
        val candidates = songs
            .filter { ArtistIdentity.sameName(it.artist, artistName) }
            .mapNotNull { it.artistId?.takeIf(String::isNotBlank) }
        if (candidates.isEmpty()) return null
        val artistCapable = catalogRepository.artistCapableIdPrefixes()
        return candidates.firstOrNull { id -> artistCapable.any(id::startsWith) } ?: candidates.first()
    }
}
