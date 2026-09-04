package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.work.WorkControls
import com.wander.android.core.playback.LIVE_SUFFIX
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.repository.AcousticFeatureRepository
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.MelodySearchRepository
import com.wander.android.data.repository.EmbeddingRepository
import com.wander.android.data.repository.RecognitionRepository
import com.wander.android.data.repository.RecordingIdentityRepository
import com.wander.android.data.repository.RecordingLinkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the landmark index over the music stored on this device.
 *
 * Decoding a library is the most expensive thing the app ever does — every track is read, decoded
 * and transformed — so it runs under the project's usual background constraints: charging, and not
 * on a low battery. No network constraint, because it touches none.
 *
 * Bounded per run rather than looping to completion. A worker that decodes a thousand files in one
 * go is a worker that gets killed a few hundred in and starts again from the same place next time;
 * a bounded batch that reschedules itself always keeps the ground it gained. [Result.retry] is
 * what asks for the next batch, and it only fires while there is genuinely more to do.
 *
 * Tracks that fail to decode are skipped, not retried. A file this device has no decoder for will
 * not acquire one, and retrying it would stall the queue behind it forever.
 */
@HiltWorker
class FingerprintIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recognitionRepository: RecognitionRepository,
    private val recordingIdentity: RecordingIdentityRepository,
    private val recordingLinks: RecordingLinkRepository,
    private val acousticFeatures: AcousticFeatureRepository,
    private val melodySearch: MelodySearchRepository,
    private val embeddingSearch: EmbeddingRepository,
    private val decoder: PcmDecoder,
    private val progress: FingerprintProgress,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls,
    private val musicRepository: MusicRepository,
    private val trackDao: com.wander.android.core.database.dao.TrackDao,
    private val trackAttemptDao: com.wander.android.core.database.dao.TrackAttemptDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // Four measurements, one decode. They answer different questions and share nothing but the
        // samples, and decoding is by far the expensive part — a separate pass for each would
        // multiply the cost of indexing a library to no purpose.
        //
        // Each is asked about **every** track, not about the tracks that need a landmark.
        //
        // That was the bug, and it was self-concealing. The run used to be driven entirely by
        // `tracksNeedingIndex()` — tracks with no landmark fingerprint — and the other three
        // questions were then asked only about *that* list. So the moment a track acquired a
        // landmark it left the candidate set for good, and could never afterwards be given a
        // melody contour or an acoustic vector. Any library indexed before hum-to-search existed
        // was frozen without one, permanently, and re-running the indexer could not fix it because
        // re-running produced the same empty list. It looked like the indexer doing nothing.
        // A pause is checked here rather than only at enqueue time. WorkManager can start a run
        // that was already queued when the user paused, and a paused job that visibly keeps working
        // is worse than a button that does nothing — at least the latter is honest.
        if (workControls.isPaused(WorkProgressNotification.Kind.FINGERPRINT).value) {
            return@withContext Result.success()
        }

        // One named track when the player asked for it, the whole library otherwise. The player's
        // request is a different urgency, not a small sweep: a track you have just started
        // listening to is the one whose missing measurement you might actually notice.
        val requestedId = inputData.getString(FingerprintIndexing.KEY_TRACK_ID)
        val candidates = recognitionRepository.fingerprintableTracks()
            .let { all -> if (requestedId == null) all else all.filter { it.id == requestedId } }
        val candidateIds = candidates.map { it.id }

        val needsFeatures = acousticFeatures.needingMeasurement(FEATURE_BATCH_LIMIT).toSet()
        // Empty while humming is off: measuring a contour is a quarter of the work of every decode,
        // and nothing reads the result. See [MelodySearch].
        val needsContour = if (com.wander.android.core.audio.melody.MelodySearch.ENABLED) {
            melodySearch.needingIndex(candidateIds).toSet()
        } else {
            emptySet()
        }
        // The neural fingerprint, on the same decode. Empty set when the model asset is absent.
        val needsEmbedding = embeddingSearch.needingIndex(EMBEDDING_BATCH_LIMIT)
            .let { needed -> needed.filterTo(mutableSetOf()) { it in candidateIds } }

        // Anything still missing any one of these is worth a decode; a track that has them all
        // is worth nothing and must not be decoded again.
        val now = System.currentTimeMillis()
        val pending = candidates.filter {
            // A track this run already failed to reach is not a candidate again.
            // Also back off tracks whose repeated failures are persisted across process deaths.
            !progress.isUnreachable(it.id) &&
            !isBackedOff(it, now) && (
                it.id in needsFeatures ||
                it.id in needsContour ||
                it.id in needsEmbedding
            )
        }
        if (pending.isEmpty()) return@withContext Result.success()

        // Foreground for the duration, and this is what makes the batching honest.
        //
        // A plain worker is killed at around ten minutes, which on a streamed library is a handful
        // of tracks — so the run ended early, deferred the rest, and from the outside looked like
        // an indexer that never finished. As a foreground service it gets to work through the
        // batch, and the person holding the phone can see that it is doing so.
        // Files on this device first, then everything else.
        //
        // Not a preference but an economy: a downloaded track is decoded straight off the disk in a
        // second or two, while a streamed one spends the better part of a minute *per window*
        // pulling audio over the network. Measured on a real library, the sweep was managing about
        // one track every four minutes and would have taken most of a night; the tracks it could
        // have done in seconds were queued behind them for no reason. Ordering this way makes the
        // index useful within minutes of a rebuild instead of by morning.
        val ordered = pending.sortedBy { track ->
            if (track.localFilePath != null || track.isDownloaded) 0 else 1
        }
        val batch = ordered.take(BATCH_SIZE)
        val eta = WorkEta(System.currentTimeMillis())
        runCatching { setForeground(notifying(eta, 0, batch.size, remaining = pending.size)) }

        for ((index, track) in batch.withIndex()) {
            if (isStopped) return@withContext Result.retry()
            // Updated before each decode rather than after, so the count names the track being
            // worked on rather than the last one finished.
            runCatching { setForeground(notifying(eta, index, batch.size, track.title, pending.size)) }
            // Marked around the decode and every write that comes off it, in a `finally` so a
            // cancelled worker or a track that fails to decode does not leave the badge spinning.
            progress.started(track.id)
            try {
                val source = audioSourceFor(track)
                if (source == null) {
                    progress.couldNotReach(track.id)
                    trackAttemptDao.recordAttempt(track.id, System.currentTimeMillis())
                    continue
                }
                val samples = decoder.decode(source.first, source.second)
                if (samples == null) {
                    progress.couldNotReach(track.id)
                    trackAttemptDao.recordAttempt(track.id, System.currentTimeMillis())
                    continue
                }
                if (track.attempts > 0) {
                    trackAttemptDao.clearAttempts(track.id)
                }
                if (track.id in needsFeatures) acousticFeatures.measure(track.id, samples)
                if (track.id in needsContour) melodySearch.index(track.id, samples)
                if (track.id in needsEmbedding) {
                    embeddingSearch.index(track.id, samples)
                    // With neural embeddings now stored, find duplicates among other indexed tracks
                    // and record links in recording_links.
                    val matches = recordingIdentity.matchesFor(track.id)
                    if (matches.isNotEmpty()) {
                        recordingLinks.record(track.id, matches)
                    }
                }
            } finally {
                progress.finished(track.id)
            }
        }

        if (ordered.size > BATCH_SIZE) Result.retry() else Result.success()
    }

    private fun notifying(
        eta: WorkEta,
        done: Int,
        total: Int,
        title: String? = null,
        remaining: Int = total
    ) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.FINGERPRINT,
            // Named for the result rather than the machinery, as the Settings row is:
            // "fingerprinting" means nothing to most people, being able to recognise a song does.
            title = "Measuring your library",
            // The track being worked on and how long is left. A bare "12 of 250" says the phone is
            // busy; the title says what it is busy *with*, and the estimate says whether this is
            // worth waiting for or worth leaving on Wi-Fi overnight.
            text = listOfNotNull(
                // Both numbers, because only one of them was ever shown and it was the small one.
                // A pass is capped at [BATCH_SIZE], so "12 of 100" on a library with a thousand
                // tracks left to measure reads as nearly finished when it is barely started.
                if (remaining > total) "$done of $total · $remaining left" else "$done of $total",
                eta.describe(done, total, System.currentTimeMillis()),
                title
            ).joinToString(" · "),
            done = done,
            total = total
        )

    /**
     * Where to read a minute of this track's audio, and what to send with the request.
     *
     * A local file when there is one, and otherwise the stream the track actually plays from.
     * Restricting this to files meant that on a library made mostly of Navidrome and YouTube Music
     * the radio and the recogniser reasoned about a handful of songs and behaved as though that
     * were the whole collection.
     *
     * Two exclusions, both of which would waste a decode rather than fail loudly. A **livestream**
     * has no beginning to measure and no fixed content to identify. A **one-shot** id names a
     * borrowed transfer that is consumed by reading it, so indexing one would spend somebody
     * else's relay session on a fingerprint.
     */
    private suspend fun audioSourceFor(track: TrackEntity): Pair<String, Map<String, String>>? {
        track.localFilePath
            ?.takeIf { it.isNotBlank() && java.io.File(it).exists() }
            ?.let { return it to emptyMap() }

        if (track.isLive || isOneShotTrackId(track.id)) return null
        val stream = musicRepository.getStreamInfo(track.id).getOrNull() ?: return null
        if (stream.uri.endsWith(LIVE_SUFFIX)) return null
        return stream.uri to stream.headers
    }

    /**
     * `internal` rather than private: [calculateBackoff] is the schedule this worker runs on, and
     * `FingerprintBackoffTest` asserts its shape — the doubling, and the 24-hour cap — which is not
     * observable any other way without waiting a day.
     */
    internal companion object {
        /**
         * Tracks per run.
         *
         * Was 25, sized for the ten minutes a *plain* worker is given before the platform kills
         * it. The run is a foreground service now, so that ceiling is gone — 25 was simply stopping
         * a library of a thousand tracks after twenty-five of them and waiting on WorkManager's
         * backoff for the next handful, which is what "it does a few and stops" looks like from
         * the outside.
         *
         * Bounded rather than unbounded for two reasons that survive the change. `Result.retry()`
         * means an interrupted run keeps every track it finished, and a bound is what makes that
         * true. And from Android 15 a `dataSync` foreground service gets roughly **six hours per
         * day across the whole app** — spending that budget on one indexing marathon would stop
         * the library sync too.
         */
        /** How many extra windows are read past the first minute. */
        private const val DEEP_WINDOWS = 3

        /**
         * How long each is.
         *
         * Fifteen seconds is several hundred landmarks — far more than a match needs — and three of
         * them cost less than doubling the first pass. Coverage was what was missing, not density.
         */
        private const val WINDOW_SECONDS = 15

        private const val BATCH_SIZE = 100

        /**
         * How far ahead to ask which tracks still need an acoustic vector.
         *
         * Derived from [BATCH_SIZE] rather than standing on its own, because standing on its own is
         * how it broke: it was a flat 200 chosen when a batch was 25, and raising the batch to 250
         * silently made it *smaller* than a run. The effect was invisible — a track missing only
         * its vector, past the two-hundredth, never entered the candidate list at all, and the
         * radio degraded with nothing to show for it. Doubled so the set comfortably covers
         * anything one run can reach.
         */
        private const val FEATURE_BATCH_LIMIT = BATCH_SIZE * 2

        /** Same reasoning as [FEATURE_BATCH_LIMIT]: comfortably larger than one run's reach. */
        private const val EMBEDDING_BATCH_LIMIT = BATCH_SIZE * 2

        const val BASE_BACKOFF_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_BACKOFF_MS = 24 * 60 * 60 * 1000L // 24 hours

        fun calculateBackoff(attempts: Int): Long {
            if (attempts <= 0) return 0L
            val shift = minOf(attempts - 1, 10)
            return minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L shl shift))
        }
    }

    internal fun isBackedOff(track: TrackEntity, now: Long): Boolean {
        if (track.attempts <= 0) return false
        val backoffMs = calculateBackoff(track.attempts)
        val last = track.lastAttemptAt ?: return false
        return (now - last) < backoffMs
    }
}
