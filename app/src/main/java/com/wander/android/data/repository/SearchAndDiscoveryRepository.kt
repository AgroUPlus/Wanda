package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.network.loggableMessage
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Handles cross-source track and album searches, remote link/track resolution, and recently added tracks.
 */
internal class SearchAndDiscoveryRepository(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val searchableSources: () -> List<IMusicSource>,
    private val activeSources: () -> List<IMusicSource>,
    private val sources: Set<IMusicSource>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit
) {
    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    suspend fun searchAllSources(
        query: String,
        onlySources: Set<SourceType>? = null,
        kind: SearchKind = SearchKind.TRACKS
    ): List<UnifiedTrack> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val allowed = allowedSources(onlySources)
        val cached = cachedTracks(query, kind, allowed.map(IMusicSource::sourceType).toSet())
        val remote = allowed
            .filter { it.capabilities.search }
            .map { source -> async { source.search(query, kind).getOrDefault(emptyList()) } }
            .flatMap { it.await() }

        persist(remote, false)
        TrackDeduplicator.deduplicate((cached + remote).distinctBy { it.id })
    }

    /**
     * [searchAllSources] for the search screen, which has to tell "nothing matched" from "nobody
     * answered". The same backends are asked the same way; the difference is that a failure is
     * reported in the [SearchOutcome] instead of being folded into an empty list.
     *
     * A separate function rather than a change to [searchAllSources]: its other callers — track
     * resolution, link and import matching, the shelves — want a best-effort list and have no
     * use for which backend was down.
     */
    suspend fun searchWithStatus(
        query: String,
        onlySources: Set<SourceType>?,
        kind: SearchKind
    ): SearchOutcome = coroutineScope {
        if (query.isBlank()) return@coroutineScope SearchOutcome()

        val allowed = allowedSources(onlySources)
        val allowedTypes = allowed.map(IMusicSource::sourceType).toSet()
        val cached = cachedTracks(query, kind, allowedTypes)
        val answers = allowed
            .filter { it.capabilities.search }
            .map { source -> source.sourceType to async { source.searchCatalog(query, kind) } }
            .map { (type, answer) -> type to answer.await() }

        // The type and a redacted message only: the exception text can carry the request URL,
        // and with it the query and the backend's credentials.
        answers.forEach { (type, answer) ->
            answer.onFailure { Log.w(TAG, "Search on $type failed: ${it.loggableMessage()}") }
        }
        val found = answers.mapNotNull { (_, answer) -> answer.getOrNull() }
        val remote = found.flatMap { it.tracks }
        val remoteAlbums = found.flatMap { it.albums }
        persist(remote, false)
        rememberAlbums(remoteAlbums)

        // Your own records, from Room, ahead of what the backends returned. Music searches only:
        // an album is not an answer to a search for videos or episodes.
        val libraryAlbums = if (kind == SearchKind.TRACKS) {
            albumDao.searchLibraryAlbums(query, MAX_LIBRARY_ALBUMS)
                .map(AlbumEntity::toUnifiedAlbum)
                .filter { it.source in allowedTypes }
        } else {
            emptyList()
        }

        SearchOutcome(
            tracks = TrackDeduplicator.deduplicate((cached + remote).distinctBy { it.id }),
            albums = (libraryAlbums + remoteAlbums).distinctBy { it.id },
            artists = found.flatMap { it.artists }.distinctBy { it.id },
            askedSources = answers.map { it.first },
            failedSources = answers.filter { it.second.isFailure }.map { it.first }
        )
    }

    /**
     * Writes the records a search turned up that Room has never seen, so the album screen they
     * open into has a header before its tracks land. Non-library, and never over a known row: a
     * search hit is a record you looked for, not one you own, and it carries less than a browsed
     * album does.
     */
    private suspend fun rememberAlbums(albums: List<UnifiedAlbum>) {
        val unknown = albums.filter { albumDao.getAlbumById(it.id) == null }
        if (unknown.isEmpty()) return
        albumDao.insertAlbums(unknown.map { AlbumEntity.fromUnifiedAlbum(it, isLibrary = false) })
    }

    private fun allowedSources(onlySources: Set<SourceType>?): List<IMusicSource> =
        searchableSources().filter { onlySources == null || it.sourceType in onlySources }

    private suspend fun cachedTracks(
        query: String,
        kind: SearchKind,
        allowedTypes: Set<SourceType>
    ): List<UnifiedTrack> = when (kind) {
        // Podcast episodes are not songs, and a downloaded one must not slip into this list
        // through the `isDownloaded` allowance below.
        SearchKind.TRACKS -> trackDao.searchTracks(query)
            .map(TrackEntity::toUnifiedTrack)
            .filter { it.source != SourceType.PODCAST && (it.source in allowedTypes || it.isDownloaded) }
        // Subscribed episodes live only in Room and have no backend to ask, so this is the
        // whole search for them.
        SearchKind.EPISODES -> trackDao.searchSubscribedEpisodes(query).map(TrackEntity::toUnifiedTrack)
        else -> emptyList()
    }

    suspend fun searchAlbums(query: String): List<UnifiedAlbum> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val known = albumDao.getAllAlbumsOnce()
            .map(AlbumEntity::toUnifiedAlbum)
            .filter { it.title.contains(query, ignoreCase = true) || query.contains(it.title, ignoreCase = true) }

        val fromTracks = searchAllSources(query)
            .filter { !it.album.isNullOrBlank() }
            .groupBy { (it.album.orEmpty()) to it.artist }
            .map { (key, tracks) ->
                val (album, artist) = key
                UnifiedAlbum(
                    id = "derived:${album.lowercase()}:${artist.lowercase()}",
                    source = tracks.first().source,
                    title = album,
                    artist = artist,
                    songCount = tracks.size,
                    year = tracks.firstNotNullOfOrNull { it.year }
                )
            }

        (known + fromTracks).distinctBy { it.title.lowercase() to it.artist.lowercase() }
    }

    suspend fun resolveTrack(
        id: String,
        title: String,
        artist: String
    ): UnifiedTrack? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(id)?.toUnifiedTrack()?.let { return@withContext it }

        val originating = SourceType.entries.firstOrNull { id.startsWith(it.idPrefix) }

        originating?.let(::sourceFor)
            ?.takeIf { it.isConfigured.value }
            ?.getTrack(id)
            ?.getOrNull()
            ?.let { return@withContext it }

        val candidates = searchAllSources("$title $artist")
            .filter { it.title.matches(title) }
        candidates.firstOrNull { it.id == id }
            ?: candidates
                .filter { it.artist.matches(artist) }
                .minByOrNull { candidate ->
                    if (candidate.source == originating) -1 else candidate.source.priority
                }
            ?: candidates.firstOrNull()
    }

    suspend fun getRecentTracks(limit: Int = 30): List<UnifiedTrack> = coroutineScope {
        val remote = activeSources()
            .map { source -> async { source.getRecentTracks(limit).getOrDefault(emptyList()) } }
            .flatMap { it.await() }
        persist(remote, true)
        TrackDeduplicator.deduplicate(
            (remote + trackDao.getRecentlyAddedTracks(limit).map(TrackEntity::toUnifiedTrack))
                .distinctBy { it.id }
        ).take(limit)
    }

    private fun String.matches(other: String): Boolean =
        normalisedForMatch() == other.normalisedForMatch()

    private fun String.normalisedForMatch(): String =
        lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

    private companion object {
        const val TAG = "SearchAndDiscovery"
        const val MAX_LIBRARY_ALBUMS = 20
    }
}
