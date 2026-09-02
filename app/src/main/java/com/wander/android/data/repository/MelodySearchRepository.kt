package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.melody.ContourMatcher
import com.wander.android.core.audio.melody.MelodyContour
import com.wander.android.core.audio.melody.PitchDetector
import com.wander.android.core.database.dao.MelodyContourDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.MelodyContourEntity
import android.util.Log
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A melody match: which track, and how badly the hum fitted it. */
data class MelodyMatch(
    val trackId: String,
    /** Average semitones of error per note. Lower is better; see [ContourMatcher.MAX_DISTANCE]. */
    val distance: Float
)

/**
 * Finds a track from a hummed, whistled or sung melody.
 *
 * The second of the two recognition engines. The landmark engine answers "what recording is
 * playing in this room" and needs the actual record; this one answers "what is that tune", from a
 * person who cannot reproduce a single sound the record makes.
 *
 * # The honest limitation
 *
 * Pitch tracking assumes one voice at a time. A hum satisfies that. A finished mix does not: YIN
 * on a full band returns whatever periodicity dominates the frame, which is often the bass line
 * rather than the tune, and sometimes the two alternate mid-phrase. So the stored contour for a
 * dense track is a rough approximation of its melody, and humming will find sparse recordings —
 * voice and guitar, solo piano — far more reliably than a wall of production.
 *
 * That is a property of doing this without a source-separation model, and it is why this engine
 * *ranks alongside* the landmark engine rather than replacing it: when the record itself is
 * playing, engine A is exact and wins. Pretending both are equally reliable would be the
 * misrepresentation; the thresholds here are set so that a bad contour returns nothing rather than
 * a plausible-looking wrong answer.
 */
@Singleton
class MelodySearchRepository @Inject constructor(
    private val contourDao: MelodyContourDao,
    private val trackDao: TrackDao,
    private val pitchDetector: PitchDetector
) {

    /**
     * The tracks whose melody best fits [samples], best first.
     *
     * Empty when the hum was too short to have a shape, or when nothing fits well enough to be
     * worth naming.
     */
    suspend fun search(samples: FloatArray, limit: Int = MAX_RESULTS): List<MelodyMatch> {
        val query = contourOf(samples)
        if (query.size < MIN_QUERY_NOTES) {
            // Logged, like every other way this returns nothing. All four were silent and
            // indistinguishable, so "it didn't find my song" could not be told from "it has never
            // measured your songs" by anybody, including from a bug report.
            Log.i(TAG, "No search: the hum gave ${query.size} notes, $MIN_QUERY_NOTES needed")
            return emptyList()
        }

        val stored = withContext(Dispatchers.IO) { contourDao.getAll(CONTOUR_VERSION) }
        if (stored.isEmpty()) {
            Log.i(TAG, "No search: no track on this device has a melody contour yet")
            return emptyList()
        }
        Log.i(TAG, "Humming ${query.size} notes against ${stored.size} measured tracks")

        return withContext(Dispatchers.Default) {
            val scored = stored
                .map { entity ->
                    MelodyMatch(
                        trackId = entity.trackId,
                        distance = ContourMatcher.distance(
                            query,
                            MelodyContour.fromBytes(entity.contour)
                        )
                    )
                }
                .filter { it.distance <= ContourMatcher.MAX_DISTANCE }
                .sortedBy { it.distance }
            Log.i(TAG, "${scored.size} of ${stored.size} fitted within the distance limit")

            // The same margin rule the landmark engine applies. Two melodies fitting equally well
            // is common — a song and its own chorus repeated, two tracks sharing a cadence — and
            // naming one of them would be a guess wearing a result's clothes.
            val best = scored.firstOrNull() ?: run {
                Log.i(TAG, "Nothing fitted well enough to name")
                return@withContext emptyList()
            }
            val runnerUp = scored.getOrNull(1)
            if (runnerUp != null && runnerUp.distance < best.distance * ContourMatcher.MIN_MARGIN) {
                Log.i(
                    TAG,
                    "Refused as ambiguous: best ${best.distance}, runner-up ${runnerUp.distance}"
                )
                return@withContext emptyList()
            }
            Log.i(TAG, "Best fit ${best.trackId} at distance ${best.distance}")
            scored.take(limit)
        }
    }

    /** Measures and stores one track's contour. Called by the indexer, on samples already decoded. */
    internal suspend fun index(trackId: String, samples: FloatArray) {
        val contour = contourOf(samples)
        if (contour.size < MelodyContour.MIN_NOTES) return
        withContext(Dispatchers.IO) {
            contourDao.upsert(
                MelodyContourEntity(
                    trackId = trackId,
                    contour = contour.toBytes(),
                    version = CONTOUR_VERSION,
                    indexedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Which tracks on this device still have no contour at this version. */
    internal suspend fun needingIndex(candidates: List<String>): Set<String> =
        withContext(Dispatchers.IO) {
            val indexed = contourDao.indexedTrackIds(CONTOUR_VERSION).toSet()
            candidates.filterNot { it in indexed }.toSet()
        }

    suspend fun clearIndex() = withContext(Dispatchers.IO) { contourDao.clear() }

    /**
     * How many tracks can be found by humming.
     *
     * Its own number, and deliberately not the landmark count. The two indexes are built by the
     * same pass but a track can easily be in one and not the other, so telling somebody "none of
     * your 103 tracks matched" after a *hum* names a set the hum was never searching.
     */
    val hummableTrackCount: kotlinx.coroutines.flow.Flow<Int> =
        contourDao.indexedTrackIdsFlow(CONTOUR_VERSION).map { it.size }

    private fun contourOf(samples: FloatArray) = MelodyContour.fromPitchTrack(
        pitches = pitchDetector.track(samples),
        framesPerSecond = AudioFormat.FRAMES_PER_SECOND
    )

    internal companion object {
        /**
         * The contour contract. Bumped when note segmentation changes — a stored contour and a
         * freshly measured one must have been cut into notes the same way, or the comparison is
         * between two different alphabets.
         *
         * Visible past this class because the fingerprint badge asks the same question the search
         * does — "is there a contour at the current version" — and a badge reading a stale version
         * would call a track indexed that the search cannot use.
         */
        const val CONTOUR_VERSION = 1

        const val MAX_RESULTS = 5

        /**
         * How many notes a *query* must have. Far more than a stored contour needs to be valid.
         *
         * The matcher aligns a subsequence: the hum may start anywhere in the stored melody and end
         * anywhere after it, which is the only way somebody humming a chorus can match a whole
         * track. The cost of that freedom is that a short query matches everything — with four
         * notes there is a close-enough run of four somewhere in almost any song, and a real
         * library duly reported all 71 measured tracks as fitting within the distance limit.
         *
         * Twelve notes is a few seconds of an actual tune, and long enough that finding it by
         * coincidence in an unrelated melody is not something that just happens.
         */
        const val MIN_QUERY_NOTES = 12

        private const val TAG = "MelodySearch"
    }
}
