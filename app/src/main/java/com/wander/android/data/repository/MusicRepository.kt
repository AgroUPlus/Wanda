package com.wander.android.data.repository

import com.wander.android.data.model.SearchKind
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ScrobbleSyncScheduler
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only thing ViewModels talk to for music data. Room is the source of truth; sources fill it.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage,
    private val connectivity: ConnectivityObserver,
    private val scrobbleSyncScheduler: ScrobbleSyncScheduler,
    private val scrobbleSuppression: ScrobbleSuppression,
    val sources: Set<@JvmSuppressWildcards IMusicSource>
) {
    /**
     * Sources that are configured *and* reachable.
     *
     * Two things mute a remote source: offline mode, and there being no network. The second used
     * to be missing entirely — with the radio off, every remote source was still asked for data
     * and every request sat there until it timed out.
     *
     * Internal rather than private so [RecommendationRepository] applies the same rule — a source
     * the user signed out of, or that offline mode has muted, must not be asked for a Home shelf
     * either.
     */
    internal fun activeSources(): List<IMusicSource> {
        val offline = secureStorage.isOfflineMode.value || !connectivity.isOnline.value
        return sources.filter { source ->
            source.isConfigured.value && (!offline || source.sourceType == SourceType.LOCAL)
        }
    }

    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    // ── Library reads (always from Room, so they work offline) ──────────────────────────────

    fun getAllTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getAllTracksFlow().mapToTracks()

    fun getLikedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getLikedTracksFlow().mapToTracks()

    fun getDownloadedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getDownloadedTracksFlow().mapToTracks()

    fun getTracksBySourceFlow(source: SourceType): Flow<List<UnifiedTrack>> =
        trackDao.getTracksBySourceFlow(source).mapToTracks()

    fun getAlbumsFlow(): Flow<List<UnifiedAlbum>> =
        albumDao.getAllAlbumsFlow().map { list -> list.map(AlbumEntity::toUnifiedAlbum) }

    /** Album ids in the order they were most recently added to. See [TrackDao.observeRecentlyAddedAlbumIds]. */
    fun getRecentlyAddedAlbumIdsFlow(limit: Int = 12): Flow<List<String>> =
        trackDao.observeRecentlyAddedAlbumIds(limit)

    fun getAlbumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId).mapToTracks()

    private fun Flow<List<TrackEntity>>.mapToTracks() =
        map { list -> list.map(TrackEntity::toUnifiedTrack) }

    // ── Playback ────────────────────────────────────────────────────────────────────────────

    /** Called by [com.wander.android.core.playback.StreamResolver] at load time. */
    suspend fun getStreamInfo(trackId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val cached = trackDao.getTrackById(trackId)
        cached?.localFilePath?.let { path ->
            return@withContext Result.success(StreamInfo(uri = path, isDirectFile = true))
        }
        val type = cached?.source ?: SourceType.entries.firstOrNull {
            trackId.startsWith(it.idPrefix)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Unrecognised track id: $trackId")
        )
        // Checked after the local-file branch above, so downloaded tracks still play. Failing here
        // is what turns "the track silently never starts" into a message the player can show: the
        // resolve runs on ExoPlayer's loading thread, where an unreachable host is a long timeout
        // rather than an error.
        // Offline mode counts here, not just a missing network. It already mutes remote sources
        // for browsing (see `activeSources`); letting it keep streaming would have made the
        // setting mean two different things, and would have made the dimmed rows in the UI a lie.
        if (type != SourceType.LOCAL &&
            (!connectivity.isOnline.value || secureStorage.isOfflineMode.value)
        ) {
            return@withContext Result.failure(
                IOException(
                    if (secureStorage.isOfflineMode.value) {
                        "Offline mode — this track is not downloaded to this device"
                    } else {
                        "No network — this track is not available on this device"
                    }
                )
            )
        }
        val source = sourceFor(type)
            ?: return@withContext Result.failure(IllegalStateException("$type is unavailable"))
        source.getStreamInfo(trackId)
    }

    /**
     * Increments the play count and queues a scrobble.
     *
     * Neither happens in incognito mode, nor while listening along with a friend — in the second
     * case because the track is their choice rather than this account's, and counting it would put
     * their listening into your history. See [ScrobbleSuppression].
     */
    suspend fun recordPlay(track: UnifiedTrack) = withContext(Dispatchers.IO) {
        if (secureStorage.isIncognitoMode || scrobbleSuppression.isSuppressed) return@withContext
        trackDao.incrementPlayCount(track.id, System.currentTimeMillis())
        val entryId = historyDao.recordHistory(HistoryEntity(trackId = track.id))
        val scrobbled = sourceFor(track.source)
            ?.takeIf { it.capabilities.scrobble }
            ?.scrobble(track.id)
            ?.isSuccess == true
        if (scrobbled) historyDao.markScrobbled(listOf(entryId))

        // The row above is also the Agro outbox entry, so the nudge belongs here rather than at a
        // caller that could forget it. Self-batching and delayed — see `syncSoon`.
        scrobbleSyncScheduler.syncSoon()
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists fetched tracks so they are available offline next time.
     *
     * [asLibrary] separates "this is the user's collection" from "this is something they merely
     * looked at". Browsing your own Navidrome albums is the former; a search hit, a radio pick or
     * an Internet Archive result is the latter. Only the former reaches the Library screen — which
     * is what stops typing in Search from growing the library.
     *
     * Sources whose catalogue is not personal ([SourceType.isPersonalLibrary]) never count as
     * library, whichever path fetched them.
     */
    private suspend fun persist(tracks: List<UnifiedTrack>, asLibrary: Boolean) {
        if (tracks.isEmpty()) return
        val libraryIds = if (asLibrary) {
            tracks.filter { it.source.isPersonalLibrary }.map { it.id }
        } else {
            emptyList()
        }
        trackDao.upsertTracks(
            tracks.map { track ->
                TrackEntity.fromUnifiedTrack(track, isLibrary = track.id in libraryIds)
            }
        )
        if (libraryIds.isNotEmpty()) trackDao.markAsLibrary(libraryIds)
    }

    // ── Searching ───────────────────────────────────────────────────────────────────────────

    /**
     * Room first so results appear instantly, then every active source in parallel. Results are
     * cached but never enter the library, and duplicates of the same recording across backends
     * collapse to the best-ranked source.
     */
    suspend fun searchAllSources(
        query: String,
        onlySources: Set<SourceType>? = null,
        kind: SearchKind = SearchKind.TRACKS
    ): List<UnifiedTrack> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val allowed = activeSources()
            // Restricting *which sources are asked* rather than filtering their results is the
            // point: a slow backend the user turned off must not hold the whole search up.
            .filter { onlySources == null || it.sourceType in onlySources }
        val allowedTypes = allowed.mapTo(mutableSetOf(), IMusicSource::sourceType)

        // Room holds every result the app has ever shown, search hits included, so signing out of
        // a backend used to leave its tracks turning up in Search for good — offered by a source
        // that is no longer there to stream them. Downloads are the exception: the file is on this
        // device and plays whatever the account does.
        // Room is only consulted for music. It has no idea whether a row was once a podcast
        // episode, so folding cached tracks into a Videos or Podcasts search would answer a
        // question the user did not ask with songs they have already seen.
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

        persist(remote, asLibrary = false)
        TrackDeduplicator.deduplicate((cached + remote).distinctBy { it.id })
    }

    // ── Writes ──────────────────────────────────────────────────────────────────────────────

    /**
     * The set of liked track ids, so a screen holding its own list of tracks (search results, the
     * queue, Now Playing) can render the heart from Room instead of from the snapshot it fetched.
     * Without this a like wrote to Room correctly but the icon never changed.
     */
    fun getLikedTrackIdsFlow(): Flow<Set<String>> =
        trackDao.getLikedTrackIdsFlow().map { it.toSet() }

    /**
     * [track] may be a search or radio result that Room has never seen, and the UPDATE behind
     * `setLiked` silently does nothing for a row that does not exist — so persist it first.
     */
    suspend fun toggleLike(track: UnifiedTrack): Result<Unit> = withContext(Dispatchers.IO) {
        val liked = !isLiked(track)
        trackDao.upsertTracks(listOf(TrackEntity.fromUnifiedTrack(track)))
        trackDao.setLiked(track.id, liked)
        val source = sourceFor(track.source)
        if (source == null || !source.capabilities.likes) return@withContext Result.success(Unit)

        // The local like stands even when the backend refuses it. Reverting looked exactly like a
        // double tap — the heart filled, then emptied a moment later — and threw away a choice the
        // user had made, for a backend they may not even be signed into. Room is the source of
        // truth for the library; the failure is reported instead of undoing the write.
        source.setLiked(track.id, liked).onFailure { cause ->
            _writeErrors.tryEmit(
                cause.message ?: "Couldn't sync that like to ${source.displayName}."
            )
        }
        Result.success(Unit)
    }

    /**
     * Failures from writes the user has already seen succeed locally — a like that could not be
     * mirrored to its backend, say. Surfaced app-wide rather than swallowed.
     */
    private val _writeErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val writeErrors: SharedFlow<String> = _writeErrors.asSharedFlow()

    /** Room, not the passed-in copy: callers hold snapshots that go stale as soon as a like lands. */
    private suspend fun isLiked(track: UnifiedTrack): Boolean =
        trackDao.getTrackById(track.id)?.isLiked ?: track.isLiked

    /**
     * Finds a track this device can actually play from another device's description of it.
     *
     * Order matters, and it is deliberately **source-first**. A track handed over from Wander
     * playing your own Navidrome carries a `navidrome:` id: that same file is what should play
     * here, at your own server's quality — not a YouTube upload of the same song that happens to
     * rank first in a cross-source search. So:
     *
     * 1. Room, by exact id — the track is already known, nothing to look up.
     * 2. The backend the id belongs to, if this device has it configured. `navidrome:42` means
     *    track 42 on the Navidrome both devices share, so it can be fetched directly.
     * 3. Only then a cross-source search on title and artist, preferring a hit from the originating
     *    source, then by source priority (local, then Navidrome, then the streaming backends).
     *
     * Step 3 is what keeps a handoff working when the other device played from a backend this one
     * does not have at all.
     */
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
                    // Same backend the session came from wins outright; otherwise the usual
                    // source ranking decides.
                    if (candidate.source == originating) -1 else candidate.source.priority
                }
            ?: candidates.firstOrNull()
    }

    /** Titles differ by punctuation and remaster suffixes across backends more often than not. */
    private fun String.matches(other: String): Boolean =
        normalisedForMatch() == other.normalisedForMatch()

    private fun String.normalisedForMatch(): String =
        lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

    // ── Remote browsing ─────────────────────────────────────────────────────────────────────

    suspend fun refreshAlbums(limit: Int = 50): List<UnifiedAlbum> = coroutineScope {
        val albums = activeSources()
            .filter { it.capabilities.albums }
            .map { source -> async { source.getAlbums(limit).getOrDefault(emptyList()) } }
            .flatMap { it.await() }
        if (albums.isNotEmpty()) {
            albumDao.insertAlbums(albums.map(AlbumEntity::fromUnifiedAlbum))
        }
        albums
    }

    suspend fun getAlbumTracks(album: UnifiedAlbum): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val tracks = sourceFor(album.source)?.getAlbumTracks(album.id)?.getOrDefault(emptyList())
        if (tracks.isNullOrEmpty()) {
            trackDao.getTracksInAlbum(album.id).map(TrackEntity::toUnifiedTrack)
        } else {
            // Browsing an album on your own server is browsing your own collection.
            persist(tracks, asLibrary = true)
            tracks
        }
    }

    suspend fun getPlaylists(): List<UnifiedPlaylist> = coroutineScope {
        activeSources()
            .filter { it.capabilities.playlists }
            .map { source -> async { source.getPlaylists().getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    suspend fun getPlaylistTracks(playlist: UnifiedPlaylist): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val tracks = sourceFor(playlist.source)
                ?.getPlaylistTracks(playlist.id)
                ?.getOrDefault(emptyList())
                .orEmpty()
            persist(tracks, asLibrary = true)
            tracks
        }

    /**
     * "Recently added" means added to *your* library, so the Internet Archive is left out: its
     * recent uploads are a public catalogue, and this call marks what it fetches as library, which
     * put strangers' uploads in the Library tab.
     */
    suspend fun getRecentTracks(limit: Int = 30): List<UnifiedTrack> = coroutineScope {
        val remote = activeSources()
            .filterNot { it.sourceType == SourceType.INTERNET_ARCHIVE }
            .map { source -> async { source.getRecentTracks(limit).getOrDefault(emptyList()) } }
            .flatMap { it.await() }
        persist(remote, asLibrary = true)
        TrackDeduplicator
            .deduplicate(
                (remote + trackDao.getRecentlyAddedTracks(limit).map(TrackEntity::toUnifiedTrack))
                    .distinctBy { it.id }
            )
            .take(limit)
    }

    /** The backends the user actually has set up, for building per-source Home shelves. */
    fun configuredSources(): List<SourceType> = activeSources().map { it.sourceType }

    /**
     * Endless radio. Falls back to the user's own most-played tracks when the source has no
     * similarity API — that is a real playlist, not a placeholder.
     */
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val source = sourceFor(seed.source)?.takeIf { it.capabilities.radio }
            val tracks = source?.getRadio(seed.id, count)?.getOrNull().orEmpty()
            if (tracks.isNotEmpty()) {
                persist(tracks, asLibrary = false)
                tracks
            } else {
                trackDao.getTopPlayedTracks(count * 2)
                    .map(TrackEntity::toUnifiedTrack)
                    .filter { it.id != seed.id }
                    .shuffled()
                    .take(count)
            }
        }
}
