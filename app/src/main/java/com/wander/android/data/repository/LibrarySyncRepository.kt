package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ContentHasher
import com.wander.android.core.sync.MediaStoreWriter
import com.wander.android.core.sync.WriteResult
import com.wander.android.data.sources.agro.AgroLibraryApi
import com.wander.android.data.sources.agro.AgroUploader
import android.content.ContentUris
import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.agro.SyncRoute
import com.wander.android.data.sources.agro.SyncMode
import com.wander.android.data.sources.agro.UploadOutcome
import com.wander.android.data.sources.navidrome.NavidromeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a *download* run got to.
 *
 * Separate from [SyncProgress], which is the upload direction. Keyed on content hash rather than
 * on position, so the detail list can mark each track done, in flight, or still waiting without
 * caring what order they were fetched in.
 */
data class FetchProgress(
    val done: Set<String> = emptySet(),
    val current: String? = null,
    val total: Int = 0,
    /** How the track in flight is actually travelling. Null until the stream opens. */
    val route: SyncRoute? = null
) {
    val running: Boolean get() = current != null
}

/** Where a sync run got to, for the Settings screen. */
data class SyncProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentTitle: String? = null
)

/**
 * Getting this device's music onto Agro, and finding out what it is missing.
 *
 * Three separate steps, deliberately, because they have very different costs:
 *
 * 1. **Hash** — reads every byte of every file. Expensive, done once per file, cached forever in
 *    `contentHash`.
 * 2. **Report** — sends metadata only. Cheap, idempotent, safe to repeat.
 * 3. **Upload** — sends the bytes, and only for files the server says it does not already have.
 *
 * Steps 1 and 2 are worth doing even if the user never uploads anything: they are what make
 * "that other device has a track you don't" work at all.
 */
@Singleton
class LibrarySyncRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val hasher: ContentHasher,
    private val libraryApi: AgroLibraryApi,
    private val uploader: AgroUploader,
    private val navidromeSource: NavidromeSource,
    private val mediaStoreWriter: MediaStoreWriter,
    private val secureStorage: SecureStorage,
    private val localSource: LocalMusicSource
) {

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val pendingUploadCount: Flow<Int> = trackDao.countPendingUploadFlow()
    val syncedCount: Flow<Int> = trackDao.countSyncedFlow()
    val localTrackCount: Flow<Int> = trackDao.countLocalFlow()

    val isEnabled: Boolean get() = libraryApi.isEnabled

    /**
     * Hashes local files that have never been hashed.
     *
     * Batched rather than done in one pass: a large library is thousands of files and tens of
     * gigabytes of reading, and a worker that runs for an hour will be killed long before it
     * finishes. Each run makes progress and the next picks up where it left off.
     */
    suspend fun hashBatch(limit: Int = HASH_BATCH): Int = withContext(Dispatchers.IO) {
        val pending = trackDao.getUnhashedLocalTracks(limit)
        var hashed = 0
        for (track in pending) {
            val uri = track.streamUri ?: continue
            val hash = hasher.hash(uri)
            if (hash != null) {
                trackDao.setContentHash(track.id, hash)
                hashed++
            }
            // A file that cannot be read is left unhashed rather than marked. It may simply have
            // been deleted since the scan, and the next scan will drop the row.
        }

        // Downloads, which were never hashed at all.
        //
        // A track fetched from Navidrome or YouTube Music is a file on this disk exactly like a
        // local one, and the peer tier addresses audio by content hash — so leaving these unhashed
        // is what made off-grid listen-along unable to name most of what the phone was holding.
        // Read by path rather than by URI: `localFilePath` has no scheme.
        for (track in trackDao.getUnhashedDownloads(limit)) {
            val path = track.localFilePath ?: continue
            val hash = hasher.hashFile(path) ?: continue
            trackDao.setContentHash(track.id, hash)
            hashed++
        }
        hashed
    }

    /**
     * Tells the server this device no longer holds these files.
     *
     * The mirror of [reportHoldings], and it had no caller at all: a track deleted from the phone
     * stayed in the server's index as something this device holds, so the fleet went on believing
     * a copy existed here and never offered it back. Called by the local scan's prune, which is
     * the one place that learns a file has gone.
     */
    suspend fun forgetHoldings(hashes: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) Result.success(0) else libraryApi.forgetHoldings(hashes)
    }

    /**
     * Reports every deletion the local scan has recorded but not yet sent.
     *
     * Cleared only on success. A failed call — offline, server down — leaves the queue intact so
     * the next attempt sends it again; dropping it would leave the server permanently wrong about
     * what this device holds, and there would be nothing left to recompute it from.
     */
    suspend fun flushPendingForget(): Result<Int> = withContext(Dispatchers.IO) {
        val pending = secureStorage.pendingForget
        if (pending.isEmpty() || !isEnabled) return@withContext Result.success(0)
        libraryApi.forgetHoldings(pending.toList()).onSuccess {
            secureStorage.pendingForget = emptySet()
        }
    }

    /**
     * Makes the server's picture of this device match reality, and returns how many claims it
     * dropped.
     *
     * `reportHoldings` only ever adds, and a deletion is only ever announced once — through the
     * prune's queue. Any notice that was lost before it landed left the server permanently
     * convinced this phone still held files it had deleted, offering them to every other device
     * from a source that could not serve them. Nothing existed to notice the drift, because the
     * phone never asked what the server thought.
     *
     * So it asks, and forgets whatever it cannot account for locally.
     *
     * Skipped while any local track is still unhashed. Content hashes are what the comparison is
     * made of, so reconciling mid-hash would read a file that is present but not yet hashed as
     * absent and forget a holding that was perfectly good — then re-report it on the next run, for
     * ever. A partial answer is worse than waiting for a complete one.
     */
    suspend fun reconcileHoldings(): Result<Int> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext Result.success(0)
        if (trackDao.getUnsyncedLocalTracks(1).isNotEmpty()) {
            return@withContext Result.success(0)
        }

        val known = trackDao.getSyncedLocalTracks().mapNotNull { it.contentHash }.toSet()
        libraryApi.deviceHoldings().mapCatching { onServer ->
            val stale = onServer.filterNot { it in known }
            if (stale.isEmpty()) 0 else libraryApi.forgetHoldings(stale).getOrThrow()
        }
    }

    /** Tells the server what this device holds. Metadata only — no audio moves. */
    suspend fun reportHoldings(): Result<Int> = withContext(Dispatchers.IO) {
        val hashed = trackDao.getUnsyncedLocalTracks(REPORT_BATCH)
        val synced = trackDao.getSyncedLocalTracks()
        libraryApi.reportHoldings(hashed + synced)
    }

    /**
     * Uploads what the server does not already have.
     *
     * A file the server reports it already holds is marked synced without any bytes moving —
     * which, after the first full run, is almost every file.
     */
    suspend fun uploadBatch(limit: Int = UPLOAD_BATCH): Int = withContext(Dispatchers.IO) {
        val pending = trackDao.getUnsyncedLocalTracks(limit)
        if (pending.isEmpty()) return@withContext 0

        _progress.value = SyncProgress(running = true, done = 0, total = pending.size)
        var uploaded = 0

        for ((index, track) in pending.withIndex()) {
            _progress.value = SyncProgress(
                running = true,
                done = index,
                total = pending.size,
                currentTitle = track.title
            )
            when (val outcome = uploader.upload(track)) {
                is UploadOutcome.Uploaded, UploadOutcome.AlreadyPresent -> {
                    trackDao.markSynced(track.id, System.currentTimeMillis())
                    uploaded++
                }
                // Left unmarked on purpose: the next run re-declares it and the server hands back
                // an offset, so the transfer continues instead of starting over.
                is UploadOutcome.Partial -> Unit
                is UploadOutcome.Failed -> _errors.tryEmit(
                    "Couldn't upload \"${track.title}\": ${outcome.reason}"
                )
            }
        }

        _progress.value = SyncProgress(running = false, done = pending.size, total = pending.size)
        if (uploaded > 0) triggerNavidromeScan()
        uploaded
    }

    /**
     * Asks Navidrome to notice the new files.
     *
     * The *client* does this, not Agro. Agro deliberately never holds a Navidrome password — its
     * settings sync carries the address and username and explicitly not the password — and this
     * device already has one. Keeping the trigger here is what preserves that property.
     */
    private suspend fun triggerNavidromeScan() {
        if (!secureStorage.navidromeConfigured.value) return
        navidromeSource.startScan().onFailure {
            // Not surfaced: the upload succeeded, and Navidrome will find the files on its own
            // next scan regardless. Worth a log, not an alarm.
            android.util.Log.i(TAG, "Navidrome scan trigger failed; its own scan will pick these up")
        }
    }

    /**
     * What another device has that this one does not — and should therefore be offered.
     *
     * Nothing is offered when the account streams from Navidrome: the track is already playable
     * here, and downloading a copy of something you can already hear is not a feature. The server
     * decides which case this is, so the phone and the desktop cannot disagree about it.
     */
    suspend fun missingHere(limit: Int = 500): Result<List<MissingTrack>> {
        val mode = libraryApi.syncMode().getOrDefault(SyncMode.PEER_TO_PEER)
        if (!mode.offersDownloads) return Result.success(emptyList())
        return libraryApi.missingOnDevice(limit)
    }

    /** Local files the server has verified it holds, so this device need not keep them. */
    suspend fun reclaimableHere(limit: Int = 50): Result<List<MissingTrack>> {
        val mode = libraryApi.syncMode().getOrDefault(SyncMode.PEER_TO_PEER)
        if (!mode.offersReclaim) return Result.success(emptyList())
        return libraryApi.reclaimable(limit)
    }

    /**
     * Pulls missing tracks down into the phone's music library.
     *
     * Stops at the first failure rather than grinding through hundreds of doomed requests —
     * whatever went wrong is very unlikely to be specific to one file. Whatever arrived before
     * that is real and is kept; the rest is offered again next time.
     */
    suspend fun fetchMissing(
        tracks: List<MissingTrack>,
        onProgress: (FetchProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var fetched = 0
        val done = mutableSetOf<String>()
        val arrivedHashes = mutableListOf<Pair<String, String>>()
        for (track in tracks) {
            onProgress(FetchProgress(done.toSet(), track.contentHash, tracks.size, null))
            val stream = uploader.fetchP2POrRelay(track).getOrElse { error ->
                return@withContext if (fetched > 0) Result.success(fetched) else Result.failure(error)
            }
            onProgress(FetchProgress(done.toSet(), track.contentHash, tracks.size, stream.route))
            val written = stream.response.use { body ->
                mediaStoreWriter.write(
                    source = body.body.byteStream(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    // The server indexed the container when it took the file, so use what it says.
                    // This was hardcoded to "flac", which named every download .flac whatever it
                    // really was — an mp3 library arrived entirely mislabelled.
                    extension = track.format?.takeIf { it.isNotBlank() } ?: "flac",
                    expectedHash = track.contentHash
                )
            }
            // Named for what actually happened. "Corrupted" was reported for an empty transfer
            // too, which sent a broken relay looking like a damaged file for as long as it took
            // somebody to read the hashing code.
            val problem = when (written) {
                is WriteResult.Written -> null
                WriteResult.Empty ->
                    "Nothing arrived for \"${track.title}\" — the device holding it did not send."
                WriteResult.HashMismatch -> "\"${track.title}\" arrived corrupted."
                is WriteResult.Failed -> "Couldn't save \"${track.title}\": ${written.reason}."
            }
            if (problem != null) {
                return@withContext if (fetched > 0) {
                    Result.success(fetched)
                } else {
                    Result.failure(java.io.IOException(problem))
                }
            }
            fetched++
            done += track.contentHash
            // Told to the server immediately. Waiting for the local scan to rediscover the file
            // meant the next pass was still offered the same track, and fetched it again.
            (written as? WriteResult.Written)?.let { result ->
                libraryApi.reportFetchedHolding(track, result.uri.toString())
                // Remember which file this is. The offer told us its hash, and hashing it again
                // locally would mean reading every byte back off the disk — the expensive pass
                // that runs only on charge. Without it the row sits with a null `contentHash`,
                // and everything keyed on that hash silently skips the file: it can never be
                // reported as gone, so deleting it is invisible to the fleet.
                arrivedHashes += "${SourceType.LOCAL.idPrefix}${ContentUris.parseId(result.uri)}" to
                    track.contentHash
            }
            onProgress(FetchProgress(done.toSet(), null, tracks.size, null))
        }
        // Applied after the scan, not before it: the rows are created by the local library scan
        // from MediaStore, and until that has run there is nothing to attach a hash to.
        if (arrivedHashes.isNotEmpty()) {
            localSource.refresh()
            arrivedHashes.forEach { (id, hash) -> trackDao.setContentHash(id, hash) }
        }
        Result.success(fetched)
    }

    suspend fun stats() = libraryApi.stats()

    /** Bytes used against the account quota. Null quota means uncapped, not zero. */
    suspend fun storageUsage() = libraryApi.storageUsage()

    /**
     * Local files the server has confirmed it holds — the only ones it is safe to offer to
     * delete, because their bytes provably exist somewhere else.
     */
    suspend fun deletableLocalTracks(): List<TrackEntity> = withContext(Dispatchers.IO) {
        // The server is asked which of these it can actually still produce — it checks its own
        // disk, not just its index. "We uploaded it once" and "it is there now" are the difference
        // between freeing space and losing a track, and only the server can tell them apart.
        //
        // Falls back to the local view when the server cannot be reached: an empty list would read
        // as "nothing to free", which is a worse lie than the old behaviour.
        val synced = trackDao.getSyncedLocalTracks()
        val confirmed = reclaimableHere(limit = MAX_RECLAIM).getOrNull() ?: return@withContext synced
        val safe = confirmed.map { it.contentHash }.toSet()
        synced.filter { it.contentHash in safe }
    }

    private companion object {
        const val TAG = "LibrarySync"

        /** Ceiling on one "what can I delete" answer, matching the server's own cap. */
        const val MAX_RECLAIM = 200

        /** Roughly a minute of hashing on a mid-range phone. */
        const val HASH_BATCH = 200
        const val REPORT_BATCH = 500
        const val UPLOAD_BATCH = 25
    }
}
