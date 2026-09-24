package com.wander.android.ui.screens.artist

import com.wander.android.core.database.entity.ArtistEntity
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.ArtistIdentity
import com.wander.android.data.repository.CatalogRepository
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
                val page = id?.let {
                    catalogRepository.artistDetails(it, if (idWasGiven) null else artist)
                }
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

    private fun findArtistId(artistName: String, songs: List<UnifiedTrack>): String? = songs
        .filter { ArtistIdentity.sameName(it.artist, artistName) }
        .firstNotNullOfOrNull { it.artistId?.takeIf(String::isNotBlank) }
}
