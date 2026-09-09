package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.MicRecorder
import com.wander.android.core.audio.melody.ContourMatcher
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import com.wander.android.core.audio.fingerprint.AudioFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton


/** Which engine produced an answer, so the UI can say how it knows. */
enum class RecognitionEngine {
    /** The record was playing, and its neural fingerprint matched — robust to the codec and
     *  timing degradation that defeated the landmark pass it replaced. Exact. */
    EMBEDDING,

    /** Somebody hummed the tune and its shape fitted. A good guess, not a certainty. */
    MELODY
}

/** What the microphone heard, when it was recognised. */
data class Recognition(
    val track: UnifiedTrack,
    /** Where in the track the listener came in, in seconds. Zero for a melody match. */
    val positionSeconds: Int,
    /** How certain this answer is, on a 0-100 scale. Higher is more certain. */
    val score: Int,
    val engine: RecognitionEngine = RecognitionEngine.EMBEDDING
)

/**
 * Why the recogniser can or cannot name anything yet.
 *
 * Three states rather than a count, because "0 tracks" has two causes that need opposite advice.
 * The index filling itself in the background is a matter of waiting; a model that was never
 * downloaded will stay missing for ever unless the user is told to fetch it. Telling the second
 * person to wait is the bug this type exists to make unrepresentable.
 */
sealed interface IndexReadiness {
    /** The ~35 MB embedder has not been downloaded. Nothing can be measured or matched. */
    data object ModelMissing : IndexReadiness

    /** The model is here and the index is still being built. */
    data object Empty : IndexReadiness

    /** [trackCount] tracks can be matched against, right now. */
    data class Ready(val trackCount: Int) : IndexReadiness

    companion object {
        /**
         * The whole decision, as a function of the two facts behind it.
         *
         * A function rather than a `when` inlined into the flow so the three branches can be
         * asserted without standing up a repository, a DAO and a 35 MB model — which is how the
         * bug this replaces survived: nothing ever checked which table the count came from.
         */
        fun of(modelReady: Boolean, indexedTrackCount: Int): IndexReadiness = when {
            !modelReady -> ModelMissing
            indexedTrackCount <= 0 -> Empty
            else -> Ready(indexedTrackCount)
        }
    }
}

/**
 * Identifies music playing in the room, against the user's own library.
 *
 * Deliberately not a Shazam. Every open fingerprinting library matches against a database you
 * supply, and no free one ships a catalogue of commercial music — so the honest feature is not
 * "name any song" but "which of *my* records is this", which needs no service, no account, and
 * sends nothing anywhere. A song the user does not own returns null rather than a guess.
 *
 * See [EmbeddingRepository] for how a recording becomes a neural fingerprint and how a clip is
 * matched against them. This class is only the microphone and the choice of engine on top.
 */
@Singleton
class RecognitionRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val micRecorder: MicRecorder,
    private val melodySearch: MelodySearchRepository,
    private val embeddingSearch: EmbeddingRepository
) {

    /**
     * How many tracks the sheet can actually match against.
     *
     * Delegated to [EmbeddingRepository] rather than counted here, because this number and the one
     * Settings shows have to be the same number. They were not: this read `fingerprints`, which no
     * longer has a writer, so the sheet said "nothing is indexed yet" while Settings counted a full
     * library of embeddings and recognition matched against them. One flow, one table, no drift.
     */
    val indexedTrackCount: Flow<Int> = embeddingSearch.indexedTrackCount

    /** [indexedTrackCount] and the reason it might be zero, as one thing the sheet can render. */
    val indexReadiness: Flow<IndexReadiness> =
        combine(embeddingSearch.modelReady, embeddingSearch.indexedTrackCount, IndexReadiness::of)

    /** Real-time microphone audio volume level `[0f, 1f]` during active capture. */
    val audioLevel: StateFlow<Float> get() = micRecorder.audioLevel

    /** Whether the microphone is open, so the UI can stop claiming it is once it is not. */
    val isRecording: StateFlow<Boolean> get() = micRecorder.isRecording

    /** How many of this device's tracks the index could cover, for the "n of m" the sheet shows. */

    suspend fun indexableTrackCount(): Int =
        withContext(Dispatchers.IO) { trackDao.getFingerprintableTrackCount() }

    /**
     * Listens, and answers as soon as it is sure.
     *
     * Null covers three different situations the caller has to tell apart by other means: the
     * microphone would not start, nothing was playing, and the music is not in the library. They
     * are the same outcome here — no track to name.
     *
     * ## Why it does not simply record for six seconds
     *
     * It used to, and then matched — so naming a song took the full clip whatever was playing,
     * even when the first two seconds were unmistakable. The microphone now hands over what it has
     * every [CHECKPOINT_SECONDS] while it keeps recording, and the first checkpoint that produces
     * a match confident enough for its length ends the capture there.
     *
     * A checkpoint is judged against [EmbeddingRepository.EARLY_MIN_SIMILARITY], deliberately
     * stricter than the bar the finished clip has to clear: two seconds of audio is a third of the
     * evidence and must not be accepted on a threshold tuned for all of it. So this is faster on
     * the easy cases — a clean recording, close to the speaker — and no less careful on the hard
     * ones, which take exactly as long as they did before.
     */
    suspend fun listen(seconds: Int = LISTEN_SECONDS): Recognition? {
        var answer: Recognition? = null
        micRecorder.stream(seconds, CHECKPOINT_SECONDS)
            .takeWhile { answer == null }
            .collect { clip ->
                // The last emission is the whole clip, and only it is judged on the ordinary
                // thresholds. `>=` rather than `==` because resampling 44.1 kHz to 8 kHz does not
                // land on an exact sample count.
                val complete = clip.size >= seconds * AudioFormat.SAMPLE_RATE
                answer = withContext(Dispatchers.Default) { identifyOrHum(clip, early = !complete) }
            }
        return answer
    }

    /**
     * Both engines, one capture.
     *
     * The microphone is opened once and the samples are handed to each engine in turn. Recording
     * twice would mean asking the user to perform twice, and the two engines want exactly the same
     * audio — a clip of a room, or a clip of somebody humming, is the same array of floats either
     * way. Which of them can do anything with it is what differs.
     *
     * The embedding engine goes first and wins outright when it answers. It is comparing the audio
     * against itself, so its answer is a fact; the melody engine is comparing a shape against a
     * shape and its answer is an inference. Running them in the other order — or blending their
     * scores — would let a plausible melody match override a certain acoustic one, and their
     * scores are not on a common scale to be blended anyway.
     */
    private suspend fun identifyOrHum(samples: FloatArray, early: Boolean = false): Recognition? {
        // The neural fingerprint is the recognition path. It replaced the landmark index, which is
        // no longer written or read: an embedding survives a lossy re-encode and a listener who
        // did not catch the track from its opening, both of which defeated the landmarks.
        val match = if (early) {
            embeddingSearch.match(
                samples,
                minSimilarity = EmbeddingRepository.EARLY_MIN_SIMILARITY,
                minMargin = EmbeddingRepository.EARLY_MIN_MARGIN
            )
        } else {
            embeddingSearch.match(samples)
        }
        match?.let { recognised ->
            val entity = withContext(Dispatchers.IO) { trackDao.getTrackById(recognised.trackId) }
            if (entity != null) {
                return Recognition(
                    track = entity.toUnifiedTrack(),
                    positionSeconds = recognised.positionSeconds.coerceAtLeast(0),
                    // A cosine in roughly [0.55, 1.0] on the 0-100 scale the melody engine's
                    // score is also mapped onto, so one confidence bar can render both.
                    score = (recognised.similarity * EMBEDDING_SCORE_SCALE).toInt(),
                    engine = RecognitionEngine.EMBEDDING
                )
            }
        }

        // Only the finished clip is offered to the melody engine: it is the weaker of the two and
        // has no business answering on a fraction of a capture.
        if (early) return null

        // Humming is switched off, and deliberately: see [MelodySearch]. The melody engine can only
        // compare a hum against a shape extracted from a finished mix, and on anything dense that
        // shape is the bass line rather than the tune — so the answers it gave were guesses wearing
        // a result's clothes. The embedding pass above is the whole feature until that is fixed
        // properly.
        if (!com.wander.android.core.audio.melody.MelodySearch.ENABLED) return null

        val hummed = melodySearch.search(samples).firstOrNull() ?: return null
        val entity = withContext(Dispatchers.IO) { trackDao.getTrackById(hummed.trackId) } ?: return null
        return Recognition(
            track = entity.toUnifiedTrack(),
            // A hum says nothing about where in the track it came from: somebody humming the
            // chorus is not listening to it, and reporting a position would be inventing one.
            positionSeconds = 0,
            // Distance is an error measure — lower is better — and `score` is a confidence, so it
            // has to be turned around rather than passed through. Scaled onto the same 0-100
            // range as an embedding score so a UI can render one bar for both.
            score = ((ContourMatcher.MAX_DISTANCE - hummed.distance) * MELODY_SCORE_SCALE).toInt(),
            engine = RecognitionEngine.MELODY
        )
    }

    /**
     * Every track that could be measured, whether or not it has been measured already.
     *
     * Every measurable track, not just the unmeasured ones: the indexer takes several different
     * measurements off one decode and they were introduced at different times. Driving the whole
     * run from "needs an embedding" would mean a track measured before a later measurement existed
     * could never acquire one — excluded from the candidate list by the fact of having been done.
     */
    internal suspend fun fingerprintableTracks(): List<TrackEntity> =
        withContext(Dispatchers.IO) { trackDao.getFingerprintableTracks() }

    internal suspend fun fingerprintableTrackIds(): List<String> =
        withContext(Dispatchers.IO) { trackDao.getFingerprintableTrackIds() }

    private companion object {
        /**
         * How long to listen.
         *
         * Long enough that a chorus's worth of segments accumulates, short enough that the user
         * is not left holding a phone at a speaker wondering whether it has frozen.
         */
        const val LISTEN_SECONDS = 6

        /**
         * How often the capture so far is offered to the matcher.
         *
         * Three, not two, so that exactly one checkpoint falls inside a six-second capture. At two
         * there were two of them, and a checkpoint match is not free: it runs on the same cores as
         * the capture and the final match queues behind whichever one is still in flight, so the
         * answer for the *whole* clip arrived later than it needed to. Measured on a Pixel 10, the
         * second checkpoint delayed the final answer by about the length of a match.
         *
         * Under two seconds there is not enough audio for even a strict threshold to mean much —
         * the model works on one-second segments — and much over three gives the recording time to
         * finish on its own, which is the thing this exists to avoid.
         */
        const val CHECKPOINT_SECONDS = 3

        /** Puts a melody match's confidence on roughly the same scale as an embedding score. */
        const val MELODY_SCORE_SCALE = 20

        /** An embedding cosine as a bar: ~0.8 similarity reads as ~80. */
        const val EMBEDDING_SCORE_SCALE = 100
    }
}
