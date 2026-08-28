package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.Fingerprinter
import com.wander.android.core.audio.fingerprint.MicRecorder
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the microphone heard, when it was recognised. */
data class Recognition(
    val track: UnifiedTrack,
    /** Where in the track the listener came in, in seconds. */
    val positionSeconds: Int,
    /** Matching landmarks behind this answer. Higher is more certain. */
    val score: Int
)

/**
 * Identifies music playing in the room, against the user's own library.
 *
 * Deliberately not a Shazam. Every open fingerprinting library matches against a database you
 * supply, and no free one ships a catalogue of commercial music — so the honest feature is not
 * "name any song" but "which of *my* records is this", which needs no service, no account, and
 * sends nothing anywhere. A song the user does not own returns null rather than a guess.
 *
 * See `Fingerprinter` for how a recording becomes landmarks. This class is only the two things
 * built on top: filling the index, and aligning a clip against it.
 */
@Singleton
class RecognitionRepository @Inject constructor(
    private val fingerprintDao: FingerprintDao,
    private val trackDao: TrackDao,
    private val micRecorder: MicRecorder,
    private val fingerprinter: Fingerprinter
) {

    val indexedTrackCount: Flow<Int> = fingerprintDao.indexedTrackCountFlow()

    /** How many of this device's tracks the index could cover, for the "n of m" the sheet shows. */
    suspend fun indexableTrackCount(): Int =
        withContext(Dispatchers.IO) { trackDao.getTracksWithLocalFiles().size }

    /**
     * Listens, then answers.
     *
     * Null covers three different situations the caller has to tell apart by other means: the
     * microphone would not start, nothing was playing, and the music is not in the library. They
     * are the same outcome here — no track to name.
     */
    suspend fun listen(seconds: Int = LISTEN_SECONDS): Recognition? {
        val samples = micRecorder.record(seconds) ?: return null
        return withContext(Dispatchers.Default) { identify(samples) }
    }

    /**
     * Aligns a clip's landmarks against the index.
     *
     * The count of shared hashes is not the answer on its own — a busy track shares scattered
     * hashes with everything. What identifies a recording is that its matches share *one* time
     * offset: if the clip really is 40 seconds into a song, then every matching landmark sits the
     * same distance from where it sits in the file. So the votes are binned by that offset and the
     * winner is the fullest bin, not the busiest track.
     */
    private suspend fun identify(samples: FloatArray): Recognition? {
        val landmarks = fingerprinter.fingerprint(samples)
        if (landmarks.isEmpty()) return null

        // Anchor frames for each hash the clip produced. One hash can occur at several moments,
        // and each occurrence is its own vote.
        val queryOffsets = HashMap<Int, MutableList<Int>>()
        for (landmark in landmarks) {
            queryOffsets.getOrPut(landmark.hash.value) { mutableListOf() } += landmark.anchorFrame
        }

        val matches = withContext(Dispatchers.IO) {
            queryOffsets.keys.chunked(SQL_VARIABLE_LIMIT).flatMap { fingerprintDao.matching(it) }
        }
        if (matches.isEmpty()) return null

        // (trackId, offset) -> votes. The offset can be negative when the clip started before the
        // matched landmark, which is ordinary and must not be discarded.
        val votes = HashMap<String, HashMap<Int, Int>>()
        for (match in matches) {
            val offsets = queryOffsets[match.hash] ?: continue
            val perTrack = votes.getOrPut(match.trackId) { HashMap() }
            for (queryFrame in offsets) {
                val delta = match.anchorFrame - queryFrame
                perTrack[delta] = (perTrack[delta] ?: 0) + 1
            }
        }

        var bestTrackId: String? = null
        var bestOffset = 0
        var bestScore = 0
        var runnerUpScore = 0
        for ((trackId, bins) in votes) {
            val (offset, score) = bins.maxByOrNull { it.value } ?: continue
            if (score > bestScore) {
                runnerUpScore = bestScore
                bestTrackId = trackId
                bestOffset = offset
                bestScore = score
            } else if (score > runnerUpScore) {
                runnerUpScore = score
            }
        }

        // Two gates, and both are needed. An absolute floor rejects a clip of silence or of a
        // room, which still produces a handful of coincidental alignments. A margin over the
        // runner-up rejects the case where two tracks are equally plausible — most often the same
        // recording indexed twice — where naming one of them would be a coin toss presented as an
        // answer.
        val trackId = bestTrackId ?: return null
        if (bestScore < MIN_SCORE) return null
        if (runnerUpScore > 0 && bestScore < runnerUpScore * MIN_MARGIN) return null

        val entity = withContext(Dispatchers.IO) { trackDao.getTrackById(trackId) } ?: return null
        return Recognition(
            track = entity.toUnifiedTrack(),
            positionSeconds = (bestOffset / AudioFormat.FRAMES_PER_SECOND).toInt().coerceAtLeast(0),
            score = bestScore
        )
    }

    /** Replaces one track's landmarks. Called by the indexer, one track at a time. */
    internal suspend fun index(track: TrackEntity, samples: FloatArray) {
        val landmarks = fingerprinter.fingerprint(samples)
        if (landmarks.isEmpty()) return
        withContext(Dispatchers.IO) {
            fingerprintDao.deleteTrack(track.id)
            fingerprintDao.insertAll(
                landmarks.map { landmark ->
                    com.wander.android.core.database.entity.FingerprintEntity(
                        hash = landmark.hash.value,
                        trackId = track.id,
                        anchorFrame = landmark.anchorFrame
                    )
                }
            )
        }
    }

    internal suspend fun tracksNeedingIndex(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val indexed = fingerprintDao.indexedTrackIds().toSet()
        trackDao.getTracksWithLocalFiles().filterNot { it.id in indexed }
    }

    suspend fun clearIndex() = withContext(Dispatchers.IO) { fingerprintDao.clear() }

    private companion object {
        /**
         * How long to listen.
         *
         * Long enough that a chorus's worth of landmarks accumulates, short enough that the user
         * is not left holding a phone at a speaker wondering whether it has frozen.
         */
        const val LISTEN_SECONDS = 6

        /** SQLite's default limit on host parameters in one statement. */
        const val SQL_VARIABLE_LIMIT = 900

        /** Matching landmarks at one offset before an answer is offered at all. */
        const val MIN_SCORE = 12

        /** How far the winner must clear the runner-up. */
        const val MIN_MARGIN = 1.6
    }
}
