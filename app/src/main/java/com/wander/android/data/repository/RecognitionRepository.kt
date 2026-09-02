package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.Fingerprinter
import com.wander.android.core.audio.fingerprint.MicRecorder
import com.wander.android.core.audio.melody.ContourMatcher
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.wander.android.core.audio.fingerprint.MatchConfidence
import com.wander.android.core.audio.fingerprint.PcmDecoder
import com.wander.android.core.audio.fingerprint.OffsetAlignment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Which engine produced an answer, so the UI can say how it knows. */
enum class RecognitionEngine {
    /** The record itself was playing, and its landmarks lined up. Exact. */
    LANDMARK,

    /** Somebody hummed the tune and its shape fitted. A good guess, not a certainty. */
    MELODY
}

/** What the microphone heard, when it was recognised. */
data class Recognition(
    val track: UnifiedTrack,
    /** Where in the track the listener came in, in seconds. Zero for a melody match. */
    val positionSeconds: Int,
    /** Matching landmarks behind this answer. Higher is more certain. */
    val score: Int,
    val engine: RecognitionEngine = RecognitionEngine.LANDMARK
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
    private val fingerprinter: Fingerprinter,
    private val melodySearch: MelodySearchRepository,
    private val secureStorage: com.wander.android.core.security.SecureStorage
) {

    val indexedTrackCount: Flow<Int> = fingerprintDao.indexedTrackCountFlow()

    /** How many of this device's tracks the index could cover, for the "n of m" the sheet shows. */
    suspend fun indexableTrackCount(): Int =
        withContext(Dispatchers.IO) { trackDao.getFingerprintableTracks().size }

    /**
     * Listens, then answers.
     *
     * Null covers three different situations the caller has to tell apart by other means: the
     * microphone would not start, nothing was playing, and the music is not in the library. They
     * are the same outcome here — no track to name.
     */
    suspend fun listen(seconds: Int = LISTEN_SECONDS): Recognition? {
        val samples = micRecorder.record(seconds) ?: return null
        return withContext(Dispatchers.Default) { identifyOrHum(samples) }
    }

    /**
     * Both engines, one capture.
     *
     * The microphone is opened once and the samples are handed to each engine in turn. Recording
     * twice would mean asking the user to perform twice, and the two engines want exactly the same
     * audio — a clip of a room, or a clip of somebody humming, is the same array of floats either
     * way. Which of them can do anything with it is what differs.
     *
     * The landmark engine goes first and wins outright when it answers. It is comparing the audio
     * against itself, so its answer is a fact; the melody engine is comparing a shape against a
     * shape and its answer is an inference. Running them in the other order — or blending their
     * scores — would let a plausible melody match override a certain acoustic one, and their
     * scores are not on a common scale to be blended anyway.
     */
    private suspend fun identifyOrHum(samples: FloatArray): Recognition? {
        identify(samples)?.let { return it }

        val match = melodySearch.search(samples).firstOrNull() ?: return null
        val entity = withContext(Dispatchers.IO) { trackDao.getTrackById(match.trackId) } ?: return null
        return Recognition(
            track = entity.toUnifiedTrack(),
            // A hum says nothing about where in the track it came from: somebody humming the
            // chorus is not listening to it, and reporting a position would be inventing one.
            positionSeconds = 0,
            // Distance is an error measure — lower is better — and `score` is a confidence, so it
            // has to be turned around rather than passed through. Scaled to sit in the same
            // rough range as a landmark score so a UI can render one bar for both.
            score = ((ContourMatcher.MAX_DISTANCE - match.distance) * MELODY_SCORE_SCALE).toInt(),
            engine = RecognitionEngine.MELODY
        )
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
        val scored = score(samples) ?: return null
        if (!scored.confidence.accepted) {
            Log.i(
                TAG,
                "Refused: a lead of ${scored.confidence.bestExcess} over the noise does not clear " +
                    "${MatchConfidence.MIN_EXCESS} and ${MatchConfidence.MIN_MARGIN}x " +
                    "the runner-up's ${scored.confidence.runnerUpExcess}"
            )
            return null
        }

        val winner = scored.ranked.first()
        val entity = withContext(Dispatchers.IO) { trackDao.getTrackById(winner.trackId) }
            ?: return null
        return Recognition(
            track = entity.toUnifiedTrack(),
            positionSeconds = (winner.offsetFrames / AudioFormat.FRAMES_PER_SECOND).toInt()
                .coerceAtLeast(0),
            score = winner.votes
        )
    }

    /**
     * Every candidate the clip aligns with, best first, and how confident that ordering is.
     *
     * Split out of [identify] so the same pass can answer two questions: "who is it" at the end,
     * and "who is it looking like so far" while the microphone is still open. Nothing here decides
     * anything — [MatchConfidence] does that, and the caller chooses whether to act on it.
     */
    private suspend fun score(samples: FloatArray): Scored? {
        val landmarks = fingerprinter.fingerprint(samples)
        if (landmarks.isEmpty()) {
            Log.i(TAG, "No landmarks in the clip — silence, or a room too quiet to hear")
            return null
        }

        // Anchor frames for each hash the clip produced. One hash can occur at several moments,
        // and each occurrence is its own vote.
        val queryOffsets = HashMap<Int, MutableList<Int>>()
        for (landmark in landmarks) {
            queryOffsets.getOrPut(landmark.hash.value) { mutableListOf() } += landmark.anchorFrame
        }

        val matches = withContext(Dispatchers.IO) {
            queryOffsets.keys.chunked(SQL_VARIABLE_LIMIT).flatMap { fingerprintDao.matching(it) }
        }
        if (matches.isEmpty()) {
            Log.i(TAG, "No indexed track shares a single hash with this clip")
            return null
        }

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

        // Neighbouring bins count: nothing makes the microphone start on a frame boundary, so one
        // true alignment arrives split across two adjacent offsets — see [OffsetAlignment].
        val ranked = votes.mapNotNull { (trackId, bins) ->
            OffsetAlignment.best(bins)?.let { Candidate(trackId, it.votes, it.offsetFrames) }
        }.sortedByDescending { it.votes }
        if (ranked.isEmpty()) return null

        val confidence = MatchConfidence.assess(ranked.map { it.votes })
        Log.i(
            TAG,
            "Landmark pass: ${landmarks.size} landmarks, ${ranked.size} candidates, " +
                "best ${ranked.first().trackId} at ${ranked.first().votes} votes, " +
                "noise floor ${confidence.noiseFloor}, lead ${confidence.bestExcess} " +
                "against ${confidence.runnerUpExcess}"
        )
        return Scored(ranked, confidence)
    }

    /** One track the clip aligned with, and where. */
    private data class Candidate(val trackId: String, val votes: Int, val offsetFrames: Int)

    private data class Scored(
        val ranked: List<Candidate>,
        val confidence: MatchConfidence.Assessment
    )

    /**
     * Replaces one track's landmarks with those of the head of the file.
     *
     * Kept as the first pass so a track is searchable as soon as its first window is read; deeper
     * windows are added by [indexWindow] and must not delete what this wrote.
     */
    internal suspend fun index(track: TrackEntity, samples: FloatArray) {
        val landmarks = fingerprinter.fingerprint(samples)
        if (landmarks.isEmpty()) return
        withContext(Dispatchers.IO) {
            fingerprintDao.deleteTrack(track.id)
            writeLandmarks(track.id, landmarks, frameOffset = 0)
        }
    }

    /**
     * Adds the landmarks of a window taken [startSeconds] into the track.
     *
     * The offset is the whole point. A landmark's anchor frame is what the matcher aligns on, so a
     * window decoded from the third minute has to be numbered from the third minute — fingerprinted
     * from zero it would claim the song opens with its own bridge, and every clip matching it would
     * be reported at a position that does not exist.
     *
     * Appends rather than replaces, so windows can be read in any order and a partially indexed
     * track stays usable for the part that has been read.
     */
    internal suspend fun indexWindow(trackId: String, samples: FloatArray, startSeconds: Int) {
        val landmarks = fingerprinter.fingerprint(samples)
        if (landmarks.isEmpty()) return
        val frameOffset = (startSeconds * AudioFormat.FRAMES_PER_SECOND).toInt()
        withContext(Dispatchers.IO) { writeLandmarks(trackId, landmarks, frameOffset) }
    }

    private suspend fun writeLandmarks(
        trackId: String,
        landmarks: List<com.wander.android.core.audio.fingerprint.Landmark>,
        frameOffset: Int
    ) {
        fingerprintDao.insertAll(
            landmarks.map { landmark ->
                com.wander.android.core.database.entity.FingerprintEntity(
                    hash = landmark.hash.value,
                    trackId = trackId,
                    anchorFrame = landmark.anchorFrame + frameOffset
                )
            }
        )
    }

    /**
     * Throws away an index built by a different version of the algorithm.
     *
     * A landmark hash means nothing outside the scheme that produced it: change how peaks are
     * picked or packed and the stored rows do not match *less well*, they match nothing at all.
     * Keeping them would leave every track looking indexed while being unfindable — which is worse
     * than an empty index, because nothing would ever go back and fix it.
     *
     * Called before the candidate list is built, so the very next sweep re-reads everything.
     */
    internal suspend fun clearIndexIfStale() = withContext(Dispatchers.IO) {
        if (secureStorage.fingerprintIndexVersion == FINGERPRINT_VERSION) return@withContext
        Log.i(
            TAG,
            "Index was built by version ${secureStorage.fingerprintIndexVersion}, " +
                "this is $FINGERPRINT_VERSION — starting again"
        )
        fingerprintDao.clear()
        secureStorage.fingerprintIndexVersion = FINGERPRINT_VERSION
    }

    internal suspend fun tracksNeedingIndex(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val depth = fingerprintDao.indexedDepth().associate { it.trackId to it.lastFrame }
        trackDao.getFingerprintableTracks().filter { track ->
            val lastFrame = depth[track.id] ?: return@filter true
            // Having landmarks is not the same as being findable. A track measured before the
            // indexer read past the first minute looks done and cannot be recognised from anywhere
            // after it, so shallow coverage counts as needing work — otherwise the very fact of
            // having been indexed once excludes it from ever being indexed properly.
            reachesPastTheHead(track, lastFrame).not()
        }
    }

    /**
     * Whether [track]'s landmarks reach past the opening window.
     *
     * True for anything short enough that the opening window *is* the whole track — there is
     * nothing deeper to read, and asking for it every sweep would re-decode a library for ever.
     */
    private fun reachesPastTheHead(track: TrackEntity, lastFrame: Int): Boolean {
        val durationSeconds = (track.durationMs / 1000L).toInt()
        // An unknown length is not evidence of a short track. Treating zero as "nothing deeper to
        // read" excused exactly the tracks most likely to need re-reading — every YouTube Music row
        // arrives without one — so it is the depth alone that decides until a length is known. The
        // indexer measures and records one on its next pass, after which this takes the real answer.
        if (durationSeconds > 0 &&
            durationSeconds <= PcmDecoder.DEFAULT_MAX_SECONDS + SHALLOW_TOLERANCE_SECONDS
        ) {
            return true
        }
        val headEnd =
            (PcmDecoder.DEFAULT_MAX_SECONDS + SHALLOW_TOLERANCE_SECONDS) * AudioFormat.FRAMES_PER_SECOND
        return lastFrame > headEnd
    }

    /**
     * Every track that could be measured, whether or not it has a landmark fingerprint already.
     *
     * Separate from [tracksNeedingIndex] because the indexer takes four different measurements off
     * one decode and they were introduced at different times. Driving the whole run from "needs a
     * landmark" meant a track indexed before the melody contour existed could never acquire one —
     * it was excluded from the candidate list by the very fact that it had already been indexed.
     */
    internal suspend fun fingerprintableTracks(): List<TrackEntity> =
        withContext(Dispatchers.IO) { trackDao.getFingerprintableTracks() }

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

        // The score floor and the margin now live in [MatchConfidence], because both are
        // meaningless as raw vote counts: what they were trying to express is a lead over the
        // noise, and the noise depends on the size of the index and the length of the clip.

        /** Puts a melody match's confidence on roughly the same scale as a landmark score. */
        const val MELODY_SCORE_SCALE = 20

        private const val TAG = "Recognition"

        /**
         * The fingerprint contract.
         *
         * Bumped whenever peak picking or hash packing changes. Version 2 replaced a stateful
         * per-band threshold with a constellation of local maxima, and linear frequency codes with
         * logarithmic ones — measured against a real microphone capture, that moved the played
         * track from 11th place to 1st.
         */
        const val FINGERPRINT_VERSION = 2

        /**
         * How far past the opening window a track's landmarks must reach to count as covered.
         *
         * A few seconds of slack, because the head pass stops on a decoder boundary rather than
         * exactly on the second, and a track re-queued every sweep for missing its last frame would
         * never leave the queue.
         */
        private const val SHALLOW_TOLERANCE_SECONDS = 5
    }
}
