package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.RecordingFingerprinter
import com.wander.android.core.database.dao.RecordingFingerprintDao
import com.wander.android.core.database.entity.RecordingFingerprintEntity
import com.wander.android.core.database.entity.RecordingSubHashEntity
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which tracks are the same recording, decided on the audio rather than on the tags.
 *
 * The existing matcher decides on normalised artist, title and duration, which is right whenever
 * the tags are right and wrong in the one case this exists for: a file whose artist field holds
 * the uploader's name, or a title carrying "(Official Video) [HQ]". Two such rows never match by
 * metadata however obviously they are the same performance, and two unrelated songs sharing a
 * common title match every time.
 *
 * Matching is two steps on purpose. The sub-hash index proposes candidates cheaply and is allowed
 * to be wrong; the sequence comparison decides and is not. Doing only the first would merge
 * unrelated recordings on a handful of coincidental hits, and doing only the second would mean
 * comparing against every fingerprint in the library.
 */
@Singleton
class RecordingIdentityRepository @Inject constructor(
    private val fingerprintDao: RecordingFingerprintDao
) {

    /**
     * How alike two sequences must be to be called one recording.
     *
     * Unrelated recordings sit at chance — half the bits agree, because that is what unrelated
     * bits do. A copy that has been through a lossy encoder sits far above that but nowhere near
     * a perfect match, so this sits between the two and nearer the noisy end, since the cost of
     * missing a merge is a duplicate row and the cost of a wrong one is hiding a track someone
     * owns.
     */
    private val matchThreshold = 0.72

    /** Records [trackId]'s fingerprint and indexes it. */
    suspend fun index(trackId: String, samples: FloatArray, durationMs: Long) {
        val hashes = RecordingFingerprinter.fingerprint(samples)
        if (hashes.isEmpty()) return

        withContext(Dispatchers.IO) {
            fingerprintDao.replace(
                RecordingFingerprintEntity(
                    trackId = trackId,
                    subHashes = hashes.toBytes(),
                    durationMs = durationMs,
                    computedAt = System.currentTimeMillis()
                ),
                halvesOf(hashes).map { RecordingSubHashEntity(it, trackId) }
            )
        }
    }

    /**
     * Track ids holding the same recording as [trackId], best first.
     *
     * Empty when [trackId] has no fingerprint yet — an unindexed track is not evidence of
     * anything, and treating it as unmatched would be indistinguishable from having checked.
     */
    suspend fun matchesFor(trackId: String): List<Match> = withContext(Dispatchers.IO) {
        val mine = fingerprintDao.forTrack(trackId) ?: return@withContext emptyList()
        val hashes = mine.subHashes.toHashes()

        val candidateIds = fingerprintDao.candidates(halvesOf(hashes).toList(), trackId)
        if (candidateIds.isEmpty()) return@withContext emptyList()

        fingerprintDao.forTracks(candidateIds)
            .map { Match(it.trackId, RecordingFingerprinter.similarity(hashes, it.subHashes.toHashes())) }
            .filter { it.similarity >= matchThreshold }
            .sortedByDescending { it.similarity }
    }

    suspend fun isIndexed(trackId: String): Boolean =
        withContext(Dispatchers.IO) { fingerprintDao.forTrack(trackId) != null }

    suspend fun forget(trackId: String) = withContext(Dispatchers.IO) {
        fingerprintDao.clearHalves(trackId)
        fingerprintDao.delete(trackId)
    }

    /**
     * Both halves of every sub-hash, each tagged with the end it came from.
     *
     * A set: a repeated hash indexes once, and a passage that repeats should not weigh more than
     * one that does not simply for having repeated.
     */
    private fun halvesOf(hashes: IntArray): Set<Int> {
        val halves = HashSet<Int>(hashes.size * 2)
        for (hash in hashes) {
            halves += hash and 0xFFFF
            halves += ((hash ushr 16) and 0xFFFF) or HIGH_HALF_TAG
        }
        return halves
    }

    data class Match(val trackId: String, val similarity: Double)

    private companion object {
        /** Keeps a low half from colliding with a high half of the same value. */
        const val HIGH_HALF_TAG = 1 shl 16

        fun IntArray.toBytes(): ByteArray {
            val buffer = ByteBuffer.allocate(size * Int.SIZE_BYTES)
            forEach(buffer::putInt)
            return buffer.array()
        }

        fun ByteArray.toHashes(): IntArray {
            val buffer = ByteBuffer.wrap(this)
            return IntArray(size / Int.SIZE_BYTES) { buffer.int }
        }
    }
}
