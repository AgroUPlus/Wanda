package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
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

        val allowed = searchableSources()
            .filter { onlySources == null || it.sourceType in onlySources }
        val allowedTypes = allowed.map(IMusicSource::sourceType).toSet()

        val cached = if (kind == SearchKind.TRACKS) {
            trackDao.searchTracks(query)
                .map(TrackEntity::toUnifiedTrack)
                .filter { it.source in allowedTypes || it.isDownloaded }
        } else {
            emptyList()
        }
        val remote = allowed
            .filter { it.capabilities.search }
            .map { source -> async { source.search(query, kind).getOrDefault(emptyList()) } }
            .flatMap { it.await() }

        persist(remote, false)
        TrackDeduplicator.deduplicate((cached + remote).distinctBy { it.id })
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
}
