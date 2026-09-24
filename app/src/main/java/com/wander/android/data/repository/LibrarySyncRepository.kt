package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ContentHasher
import com.wander.android.core.sync.MediaStoreWriter
import com.wander.android.data.sources.agro.AgroLibraryApi
import com.wander.android.data.sources.agro.AgroUploader
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.agro.SyncMode
import com.wander.android.data.sources.agro.UploadOutcome
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Getting this device's music onto Agro, and finding out what it is missing.
 *
 * 1. **Hash** — reads every byte of every file. Expensive, done once per file, cached forever in `contentHash`.
 * 2. **Report** — sends metadata only. Cheap, idempotent, safe to repeat.
 * 3. **Upload** — sends the bytes, and only for files the server says it does not already have.
 */
@Singleton
class LibrarySyncRepository @Inject internal constructor(
    private val trackDao: TrackDao,
    private val hasher: ContentHasher,
    private val libraryApi: AgroLibraryApi,
    private val uploader: AgroUploader,
    private val navidromeSource: NavidromeSource,
    private val secureStorage: SecureStorage,
    private val missingFetcher: LibraryMissingFetcher
) {
    constructor(
        trackDao: TrackDao,
        hasher: ContentHasher,
        libraryApi: AgroLibraryApi,
        uploader: AgroUploader,
        navidromeSource: NavidromeSource,
        mediaStoreWriter: MediaStoreWriter,
        secureStorage: SecureStorage,
        localSource: LocalMusicSource
    ) : this(
        trackDao,
        hasher,
        libraryApi,
        uploader,
        navidromeSource,
        secureStorage,
        LibraryMissingFetcher(trackDao, libraryApi, uploader, mediaStoreWriter, localSource)
    )

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
        }

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
     */
    suspend fun forgetHoldings(hashes: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) Result.success(0) else libraryApi.forgetHoldings(hashes)
    }

    /**
     * Reports every deletion the local scan has recorded but not yet sent.
     */
    suspend fun flushPendingForget(): Result<Int> = withContext(Dispatchers.IO) {
        val pending = secureStorage.pendingForget
        if (pending.isEmpty() || !isEnabled) return@withContext Result.success(0)
        libraryApi.forgetHoldings(pending.toList()).onSuccess {
            secureStorage.pendingForget = emptySet()
        }
    }

    /**
     * Makes the server's picture of this device match reality, and returns how many claims it dropped.
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

    private suspend fun triggerNavidromeScan() {
        if (!secureStorage.navidromeConfigured.value) return
        navidromeSource.startScan().onFailure {
            android.util.Log.i(TAG, "Navidrome scan trigger failed; its own scan will pick these up")
        }
    }

    /**
     * What another device has that this one does not — and should therefore be offered.
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
     */
    suspend fun fetchMissing(
        tracks: List<MissingTrack>,
        onProgress: (FetchProgress) -> Unit = {}
    ): Result<Int> = missingFetcher.fetchMissing(tracks, onProgress)

    suspend fun stats() = libraryApi.stats()

    /** Bytes used against the account quota. Null quota means uncapped, not zero. */
    suspend fun storageUsage() = libraryApi.storageUsage()

    /**
     * Local files the server has confirmed it holds — the only ones it is safe to offer to delete.
     */
    suspend fun deletableLocalTracks(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val synced = trackDao.getSyncedLocalTracks()
        val confirmed = reclaimableHere(limit = MAX_RECLAIM).getOrNull() ?: return@withContext synced
        val safe = confirmed.map { it.contentHash }.toSet()
        synced.filter { it.contentHash in safe }
    }

    private companion object {
        const val TAG = "LibrarySync"
        const val MAX_RECLAIM = 200
        const val HASH_BATCH = 200
        const val REPORT_BATCH = 500
        const val UPLOAD_BATCH = 25
    }
}
