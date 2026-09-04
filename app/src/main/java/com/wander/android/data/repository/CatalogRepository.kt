package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.ArtistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the album and artist pages read.
 *
 * Room first, always: an album you have opened before renders instantly and works offline. The
 * backend is asked afterwards to fill in what Room has not seen — a Navidrome album browsed for
 * the first time, or an artist whose later records were never fetched.
 *
 * Artists are keyed by **name**, not by id. Only Navidrome gives tracks a stable `artistId`;
 * YouTube Music rows carry none, so an id-keyed artist page would be empty for every streaming
 * source. The name is the one identifier every backend actually provides.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val musicRepository: MusicRepository
) {

    // ── Album ───────────────────────────────────────────────────────────────────────────────

    fun albumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId)
            .map { it.map(TrackEntity::toUnifiedTrack) }
            .flowOn(Dispatchers.Default)

    suspend fun album(albumId: String): UnifiedAlbum? = withContext(Dispatchers.IO) {
        albumDao.getAlbumById(albumId)?.toUnifiedAlbum() ?: albumFromTracks(albumId)
    }

    /**
     * Pulls the album's tracks from its backend and persists them, so the flow above fills in.
     *
     * Two paths, because there are two ways to arrive here. When Room knows the album,
     * [MusicRepository.getAlbumTracks] is used — it marks an album browsed on your own server as
     * part of your library, which is the right claim for a record you host yourself.
     *
     * When it does not, the album is resolved from its id prefix instead. That branch is not an
     * edge case: an album tapped in an artist's shelf has no `AlbumEntity` row *and* no tracks, so
     * [album] returns null for it, and this used to return here without ever asking the backend —
     * leaving every YouTube Music album opened from an artist page permanently empty.
     */
    suspend fun refreshAlbum(albumId: String) {
        val album = album(albumId)
        if (album != null) {
            musicRepository.getAlbumTracks(album)
        } else {
            musicRepository.getAlbumTracksById(albumId)
        }
    }

    /**
     * Writes album rows the app has seen but not browsed — the shelves on an artist's page.
     *
     * Without this the album screen has no header until its tracks land, and then only the one
     * [albumFromTracks] can reconstruct from them. Non-library, for the same reason the tracks
     * are: seeing a record on an artist page is not owning it.
     */
    suspend fun rememberAlbums(albums: List<UnifiedAlbum>) = withContext(Dispatchers.IO) {
        // Only records Room has never seen. `insertAlbums` replaces on conflict, and a tile off an
        // artist shelf carries no track count and no duration — writing it over a Navidrome album
        // that has actually been browsed would blank fields the library screen shows.
        val known = albums.mapNotNull { album -> albumDao.getAlbumById(album.id)?.let { album to it } }

        // Rows Room already has get their credit corrected if it has changed. Without this a bad
        // one was permanent — inserts skip known albums, so an album once filed under an artist
        // called "Single" stayed there however many times its artist's page was opened.
        for ((incoming, stored) in known) {
            if (incoming.artist.isNotBlank() && incoming.artist != stored.artist) {
                albumDao.updateAlbumArtist(stored.id, incoming.artist, incoming.artistId)
            }
        }

        val unknown = albums.filter { album -> known.none { it.second.id == album.id } }
        if (unknown.isEmpty()) return@withContext
        // Left non-library: a tile on an artist's page is a record you have looked at, and
        // filing those into the Library tab is what made it list every artist you ever opened.
        albumDao.insertAlbums(unknown.map { AlbumEntity.fromUnifiedAlbum(it, isLibrary = false) })
    }

    /**
     * An album Room knows the *tracks* of but has no row for — the usual case for YouTube Music,
     * whose album rows only arrive by browsing the library. Assembled from the tracks rather than
     * left blank, since every field the header needs is already on them.
     */
    private suspend fun albumFromTracks(albumId: String): UnifiedAlbum? {
        val tracks = trackDao.getTracksInAlbum(albumId).map(TrackEntity::toUnifiedTrack)
        val first = tracks.firstOrNull() ?: return null
        return UnifiedAlbum(
            id = albumId,
            source = first.source,
            title = first.album ?: first.title,
            artist = first.artist,
            artistId = first.artistId,
            coverArtUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
            songCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            year = first.year,
            genre = first.genre
        )
    }

    // ── Artist ──────────────────────────────────────────────────────────────────────────────

    /**
     * [artistId] is the backend's id for whoever's page this is, once it is known. Items credited
     * to a *different* id are a different artist who happens to share the name — see
     * [ArtistIdentity].
     */
    fun artistAlbumsFlow(artist: String, artistId: String? = null): Flow<List<UnifiedAlbum>> =
        albumDao.getAlbumsByArtistFlow(artist).map { entities ->
            ArtistIdentity.sameArtist(entities, artistId) { it.artistId }
                .map(AlbumEntity::toUnifiedAlbum)
        }.flowOn(Dispatchers.Default)

    /**
     * Deduplicated: the artist page is fed by a cross-source search, so the same song arrives once
     * from Navidrome and once from YouTube Music. [TrackDeduplicator] keeps the copy from the
     * lowest-priority source — your own files and your own server before anything streamed.
     */
    fun artistTracksFlow(artist: String, artistId: String? = null): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByArtistFlow(artist).map { entities ->
            TrackDeduplicator.deduplicate(
                ArtistIdentity.sameArtist(entities, artistId) { it.artistId }
                    .map(TrackEntity::toUnifiedTrack)
            )
        }.flowOn(Dispatchers.Default)

    /**
     * Fills in an artist Room only partly knows, by searching every configured backend for their
     * name and persisting the hits.
     *
     * A search, rather than a per-source artist endpoint: only Navidrome has one, and the point of
     * this page is that it works the same whichever backend the track came from. Results are
     * persisted as non-library by [MusicRepository.searchAllSources], so browsing an artist does
     * not silently grow the Library tab.
     */
    suspend fun refreshArtist(artist: String) {
        musicRepository.searchAllSources(artist)
    }

    /** What is already known about this artist, or null if they have never been opened. */
    suspend fun cachedArtist(artist: String): ArtistEntity? = withContext(Dispatchers.IO) {
        artistDao.getByName(artist.lowercase())
    }

    /**
     * Remembers the identity half of an artist's page.
     *
     * Called after a successful fetch, including one that found no backend page — a null
     * [details] still records that we looked, which is what stops the next visit paying for the
     * same disappointment behind a skeleton.
     */
    suspend fun cacheArtist(artist: String, details: ArtistDetails?) = withContext(Dispatchers.IO) {
        artistDao.upsert(
            ArtistEntity(
                nameKey = artist.lowercase(),
                name = details?.name ?: artist,
                artistId = details?.id,
                imageUrl = details?.imageUrl,
                bio = details?.bio,
                fetchedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Whether a cached artist is recent enough to skip the cross-source search on this visit.
     *
     * The search is the expensive half — it asks every configured backend for the artist's name —
     * and a discography does not change between two visits a few minutes apart. Stale entries
     * still render instantly from cache; they simply refresh underneath the page rather than in
     * front of it.
     */
    fun isFresh(cached: ArtistEntity): Boolean =
        System.currentTimeMillis() - cached.fetchedAt < ARTIST_CACHE_MS

    /**
     * The artist's own page from the backend that has one.
     *
     * The name-keyed view above is still what the screen is built on — it gathers everything by
     * that artist across every source, which no single backend can do. This adds what only the
     * backend knows: the bio, their portrait, and the shelves *they* arrange their work into.
     * Sources that do not publish artist pages are skipped rather than approximated; see
     * `SourceCapabilities.artists`.
     *
     * Null when nothing was reachable, which the screen treats as "no extra page", not an error —
     * the library-derived one underneath it is still perfectly good.
     */
    suspend fun artistDetails(artistId: String): ArtistDetails? = withContext(Dispatchers.IO) {
        val source = musicRepository.sources.firstOrNull {
            it.capabilities.artists && artistId.startsWith(it.sourceType.idPrefix)
        } ?: return@withContext null
        source.getArtist(artistId).getOrNull()
    }

    /**
     * The whole of one album shelf on an artist's page.
     *
     * [browseId] and [params] are the coordinates the shelf's own "more" button carried, so the
     * source that produced the shelf is the one asked to expand it. Empty on failure, which the
     * screen treats as "nothing more arrived" and leaves the shelf as it was.
     */
    suspend fun artistAlbumPage(
        browseId: String,
        params: String?,
        artist: String
    ): List<UnifiedAlbum> = withContext(Dispatchers.IO) {
        val source = musicRepository.sources.firstOrNull {
            it.capabilities.artists && browseId.startsWith(it.sourceType.idPrefix)
        } ?: return@withContext emptyList()
        source.getArtistAlbumPage(browseId, params, artist).getOrDefault(emptyList())
    }

    /** Cover for the artist header: whichever of their records has one. */
    fun artistImage(albums: List<UnifiedAlbum>, tracks: List<UnifiedTrack>): String? =
        albums.firstNotNullOfOrNull { it.coverArtUrl }
            ?: tracks.firstNotNullOfOrNull { it.artworkUrl }

    /** Which backends this artist's known material came from, for the page's subtitle. */
    fun sourcesOf(tracks: List<UnifiedTrack>): List<SourceType> =
        tracks.map { it.source }.distinct().sorted()
}

/** How long an artist page is reused before the backend is asked again. */
private const val ARTIST_CACHE_MS = 6 * 60 * 60 * 1000L
