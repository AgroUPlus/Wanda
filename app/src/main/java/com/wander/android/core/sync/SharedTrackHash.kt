package com.wander.android.core.sync

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.data.model.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The content hash of a track this device is about to offer to somebody, computed if it has to be.
 *
 * Every peer tier — LAN, off-grid radio, and the relay — asks for *bytes by hash*, because a peer's
 * server answers for files and not for titles. Until now the only thing that ever computed a hash
 * was [LibrarySyncWorker], which runs unmetered and charging and only when library sync is turned
 * on. So on a phone that had never completed a sync, every local file was announced to friends with
 * no hash, and all three transfer tiers correctly declined a transfer they had no way to name.
 *
 * From the listener's side that is indistinguishable from a bug: two phones on one Wi-Fi, one
 * playing a file, and the other saying it cannot find it.
 *
 * Hashing one file the user is actively sharing is a very different proposition from hashing a
 * library: it is a single read of a file that is already open for playback, at the moment its owner
 * asked for it to be shared. The batch worker stays exactly as it was, and the result is written
 * back to Room, so this converges to a no-op as sync catches up.
 */
@Singleton
class SharedTrackHash @Inject constructor(
    private val trackDao: TrackDao,
    private val hasher: ContentHasher
) {

    /**
     * Serialises hashing, so a track transition and a jam add landing together do not read the
     * same file twice. Contention is a non-issue — one file at a time is the whole design.
     */
    private val mutex = Mutex()

    /**
     * The stored hash, a freshly computed one, or null when there can never be one.
     *
     * Null is a real answer and the callers rely on it: a track that is only ever streamed is not
     * something this device could hand over, and an unreadable file has been deleted since the scan.
     */
    suspend fun of(trackId: String): String? = mutex.withLock {
        val track = trackDao.getTrackById(trackId) ?: return null
        track.contentHash?.let { return it }
        // Keyed on *having a file*, not on the source it came from. A downloaded Navidrome or
        // YouTube Music track keeps its original source on purpose — `RenditionFinder` ranks
        // offline-first and needs to know where it came from — so testing the source excluded
        // every download from sharing, while its bytes sat on the disk all along. `TrackDao`'s
        // fingerprint query already uses this rule; this one had drifted from it.
        // A download is a plain filesystem path, not a `content://` URI, so it is wrapped as one
        // — `ContentHasher` reads everything through the resolver, which handles `file://`.
        val uri = track.localFilePath
            ?.let { java.io.File(it) }
            ?.takeIf { it.exists() }
            ?.let { android.net.Uri.fromFile(it) }
            ?: track.streamUri?.takeIf { track.source == SourceType.LOCAL }?.let(android.net.Uri::parse)
            ?: return null
        val hash = hasher.hash(uri) ?: return null
        trackDao.setContentHash(trackId, hash)
        hash
    }
}
