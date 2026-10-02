package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.ArtistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.musicbrainz.MusicBrainzArtistFallback
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
    private val musicRepository: MusicRepository,
    private val musicBrainzFallback: MusicBrainzArtistFallback
) {
    private val albums = CatalogAlbumRepository(trackDao, albumDao, musicRepository)

    // ── Album ───────────────────────────────────────────────────────────────────────────────
    // Delegated to [CatalogAlbumRepository] — see its doc for why this file only forwards to it.

    fun albumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> = albums.albumTracksFlow(albumId)
    suspend fun album(albumId: String): UnifiedAlbum? = albums.album(albumId)
    suspend fun refreshAlbum(albumId: String) = albums.refreshAlbum(albumId)
    suspend fun rememberAlbums(albums: List<UnifiedAlbum>) = this.albums.rememberAlbums(albums)

    // ── Artist ──────────────────────────────────────────────────────────────────────────────

    /**
     * [artistId] is the backend's id for whoever's page this is, once it is known. Items credited
     * to a *different* id are a different artist who happens to share the name — see
     * [ArtistIdentity].
     */
    fun artistAlbumsFlow(artist: String, artistId: String? = null): Flow<List<UnifiedAlbum>> =
        kotlinx.coroutines.flow.combine(
            albumDao.getAlbumsByArtistFlow(artist),
            trackDao.getTracksByArtistFlow(artist)
        ) { albumEntities, trackEntities ->
            // The DAO query is a broad `LIKE`, so this is where "Artisan Collective" gets dropped
            // from a page for "Art" — see `ArtistIdentity.creditsMatch`'s own doc.
            val albums = albumEntities.filter { ArtistIdentity.creditsMatch(it.artist, artist) }
            val tracks = trackEntities.filter { ArtistIdentity.creditsMatch(it.artist, artist) }
                .map(TrackEntity::toUnifiedTrack)
            val aliases = ArtistIdentity.aliasesOf(tracks, artistId)
            ArtistIdentity.sameArtist(albums, aliases) { it.artistId }
                .map(AlbumEntity::toUnifiedAlbum)
        }.flowOn(Dispatchers.Default)

    /**
     * Deduplicated: the artist page is fed by a cross-source search, so the same song arrives once
     * from Navidrome and once from YouTube Music. [TrackDeduplicator] keeps the copy from the
     * lowest-priority source — your own files and your own server before anything streamed.
     */
    fun artistTracksFlow(artist: String, artistId: String? = null): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByArtistFlow(artist).map { entities ->
            val tracks = entities
                .filter { ArtistIdentity.creditsMatch(it.artist, artist) }
                .map(TrackEntity::toUnifiedTrack)
            val aliases = ArtistIdentity.aliasesOf(tracks, artistId)
            TrackDeduplicator.deduplicate(ArtistIdentity.sameArtist(tracks, aliases) { it.artistId })
        }.flowOn(Dispatchers.Default)

    /**
     * A one-shot read of everything Room has that credits [artist], for resolving which backend id
     * is worth fetching a page for right after a cross-source search — see
     * `ArtistCatalogLoader.refresh`, which used to ask this with whatever track list it already had
     * *before* that search ran, so a first-ever visit to an artist's page always searched for an id
     * among zero tracks and fell straight to the MusicBrainz fallback even when the search it had
     * just run found plenty.
     */
    suspend fun tracksByArtist(artist: String): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getTracksByArtistOnce(artist)
            .filter { ArtistIdentity.creditsMatch(it.artist, artist) }
            .map(TrackEntity::toUnifiedTrack)
    }

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
     *
     * A row with no [ArtistEntity.artistId] is never fresh, however recently it was written. Such a
     * row records a visit that ended up knowing nothing, and the search is the very thing that
     * would have fixed that — skipping it leaves the page with no tracks, therefore no id to infer,
     * therefore nothing to fetch, and the next visit writes the same empty row again. One failed
     * lookup became six hours of "nothing by this artist in any source" for an artist whose work
     * was a search away.
     */
    fun isFresh(cached: ArtistEntity): Boolean =
        cached.artistId != null &&
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
     *
     * [expectedName] is the name the caller was browsing, when they were browsing a name at all.
     * Null means the id came from somewhere authoritative — a shared link, a tapped tile — where
     * the id *is* the identity and there is nothing to check it against. It is only supplied when
     * the id was inferred from a name, which is the one way it can be about the wrong person.
     */
    suspend fun artistDetails(
        artistId: String,
        expectedName: String? = null
    ): ArtistFetch = withContext(Dispatchers.IO) {
        val source = musicRepository.sources.firstOrNull {
            it.capabilities.artists && artistId.startsWith(it.sourceType.idPrefix)
        } ?: return@withContext ArtistFetch.NotFound
        val result = source.getArtist(artistId)
        val page = result.getOrNull()
        if (page == null) {
            val error = result.exceptionOrNull()
            // A source returning `null`-through-`Result.failure` with nothing thrown (a well-formed
            // "no such artist" answer) is [ArtistFetch.NotFound], not a failure — only an actual
            // exception (network, parsing) is worth telling the screen apart from "no page exists".
            if (error != null) {
                Log.w(TAG, "Artist fetch failed for $artistId: ${error.javaClass.simpleName}")
                return@withContext ArtistFetch.Failed(error)
            }
            return@withContext ArtistFetch.NotFound
        }

        // The page has to be about the artist we asked for, or it is not this artist's page.
        //
        // [artistId] can be wrong through no fault of the caller: where nobody passed an id, it is
        // picked off a track Room matched by *name*, and Room folds case deliberately so that one
        // artist spelled two ways stays together. On a name two artists share, that picks one of
        // them at random — and the fetch then succeeds, returning a complete, genuine page about
        // the other person. That is how a portrait of a stranger arrives with a matching biography
        // and no error anywhere: nothing downstream could tell, because nothing downstream knows
        // which name was asked for. This is the only place that does.
        //
        // Rejected rather than repaired. A page about somebody else has nothing salvageable on it,
        // and the artist screen renders perfectly well from the library alone with a monogram at
        // the top — see `ArtistHero`. Deliberate, so [ArtistFetch.NotFound] rather than
        // [ArtistFetch.Failed]: nothing here is broken, the id just did not name who we thought.
        if (expectedName != null && !ArtistIdentity.sameName(page.name, expectedName)) {
            Log.i(TAG, "Artist page name mismatch for $artistId: fetched name did not match $expectedName")
            return@withContext ArtistFetch.NotFound
        }
        ArtistFetch.Found(withGenres(page))
    }

    /**
     * Adds MusicBrainz genre tags to a page that already knows its own [ArtistDetails.musicBrainzId]
     * — Navidrome's `getArtistInfo2` gives the id but never the tags. Best-effort: [genresFor]
     * already swallows its own failures and the consent check, so a page with no genres back is
     * just a page with no genres, the same as any backend that never had any to give.
     */
    private suspend fun withGenres(page: ArtistDetails): ArtistDetails {
        val mbid = page.musicBrainzId ?: return page
        val genres = musicBrainzFallback.genresFor(mbid)
        return if (genres.isEmpty()) page else page.copy(genres = genres)
    }

    /** Id prefixes of backends that publish artist pages — see [artistDetails]. */
    fun artistCapableIdPrefixes(): Set<String> =
        musicRepository.sources.filter { it.capabilities.artists }.map { it.sourceType.idPrefix }.toSet()

    /**
     * A last-resort page for an artist no configured source could name an id for — see
     * [MusicBrainzArtistFallback], which this only forwards to.
     */
    suspend fun musicBrainzArtistFallback(name: String): ArtistFetch =
        withContext(Dispatchers.IO) { musicBrainzFallback.lookup(name) }

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
        source.getArtistAlbumPage(browseId, params, artist)
            .onFailure { Log.w(TAG, "Artist album shelf fetch failed for $browseId: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "CatalogRepository"
    }
}

/** How long an artist page is reused before the backend is asked again. */
private const val ARTIST_CACHE_MS = 6 * 60 * 60 * 1000L
