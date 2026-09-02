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
    private val decoder: PcmDecoder,
    private val progress: FingerprintProgress,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls,
    private val musicRepository: MusicRepository,
    private val trackDao: com.wander.android.core.database.dao.TrackDao
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

        val needsLandmark = recognitionRepository.tracksNeedingIndex().mapTo(mutableSetOf()) { it.id }
        val needsCanonical = recordingIdentity.needingIndex(candidateIds).toSet()
        val needsFeatures = acousticFeatures.needingMeasurement(FEATURE_BATCH_LIMIT).toSet()
        val needsContour = melodySearch.needingIndex(candidateIds).toSet()

        // Anything still missing any one of the four is worth a decode; a track that has all four
        // is worth nothing and must not be decoded again.
        val pending = candidates.filter {
            // A track this run already failed to reach is not a candidate again. Without this the
            // failures sit at the head of the list for ever and the sweep re-spends every batch on
            // them — see `FingerprintProgress.couldNotReach`.
            !progress.isUnreachable(it.id) && (
                it.id in needsLandmark ||
                it.id in needsCanonical ||
                it.id in needsFeatures ||
                it.id in needsContour
            )
        }
        if (pending.isEmpty()) return@withContext Result.success()

        // Foreground for the duration, and this is what makes the batching honest.
        //
        // A plain worker is killed at around ten minutes, which on a streamed library is a handful
        // of tracks — so the run ended early, deferred the rest, and from the outside looked like
        // an indexer that never finished. As a foreground service it gets to work through the
        // batch, and the person holding the phone can see that it is doing so.
        val batch = pending.take(BATCH_SIZE)
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
                    continue
                }
                val samples = decoder.decode(source.first, source.second)
                if (samples == null) {
                    progress.couldNotReach(track.id)
                    continue
                }
                // Guarded now that the list is a union: a track pulled in because it wants a
                // contour must not have its landmarks written a second time.
                if (track.id in needsLandmark) {
                    recognitionRepository.index(track, samples)
                    indexDeeperWindows(track, source)
                }
                if (track.id in needsCanonical) {
                    recordingIdentity.index(track.id, samples, track.durationMs)
                    // Asked here and not lazily at read time: the comparison needs every candidate
                    // fingerprint in memory, which is affordable once per track in a background
                    // worker and not affordable on every library query. This is where the answer
                    // gets written down, and it is the only thing that turns a stored fingerprint
                    // into a merge.
                    recordingLinks.record(track.id, recordingIdentity.matchesFor(track.id))
                }
                if (track.id in needsFeatures) acousticFeatures.measure(track.id, samples)
                if (track.id in needsContour) melodySearch.index(track.id, samples)
            } finally {
                progress.finished(track.id)
            }
        }

        if (pending.size > BATCH_SIZE) Result.retry() else Result.success()
    }

    /**
     * Reads a few more windows from further into the track, so it can be recognised past its head.
     *
     * The first pass indexes a minute from the start, which is ample *density* — thousands of
     * landmarks — and no coverage at all beyond it. A clip is taken from wherever the listener is
     * standing, and a clip from the third minute of a song shares literally no landmarks with an
     * index built from its first: the answer is not weak, it is absent. That is the shape of "it
     * cannot find a song I own and have already indexed".
     *
     * Windows rather than the whole track, because decoding is paid per second of audio and a
     * streamed library pays for it twice, in data as well as time. Spread across what is left so
     * that a chorus, a bridge and an outro each have something in the index.
     *
     * Failures are silent and per window: a seek that lands badly or a stream that stops early
     * costs that window's coverage and nothing else, and the track keeps the landmarks it already
     * has.
     */
    private suspend fun indexDeeperWindows(track: TrackEntity, source: Pair<String, Map<String, String>>) {
        // The row's own duration first, then the container's. A YouTube Music track routinely
        // arrives with none — 175 of them in one real library — and reading the field alone meant
        // this returned immediately for every one of them, which is to say it never ran at all.
        val durationSeconds = (track.durationMs / 1000L).toInt().takeIf { it > 0 }
            ?: decoder.durationSeconds(source.first, source.second)?.also { measured ->
                // Written back, so the next sweep can reason about this track without opening it
                // again — and so everything else that gates on a duration stops seeing zero.
                trackDao.fillMissingDuration(track.id, measured * 1000L)
            }
            ?: return

        // Nothing beyond the head to read. The `+ WINDOW_SECONDS` keeps a track that is barely
        // longer than the first pass from being decoded again for a sliver.
        if (durationSeconds <= PcmDecoder.DEFAULT_MAX_SECONDS + WINDOW_SECONDS) return

        val remaining = durationSeconds - PcmDecoder.DEFAULT_MAX_SECONDS
        val windows = minOf(DEEP_WINDOWS, remaining / WINDOW_SECONDS)
        if (windows <= 0) return

        // Evenly through what the head did not reach, and never so close to the end that the window
        // would run off it.
        val step = remaining / (windows + 1)
        for (i in 1..windows) {
            if (isStopped) return
            val start = PcmDecoder.DEFAULT_MAX_SECONDS + step * i
            if (start + WINDOW_SECONDS > durationSeconds) continue
            val samples = runCatching {
                decoder.decode(source.first, source.second, WINDOW_SECONDS, startSeconds = start)
            }.getOrNull() ?: continue
            recognitionRepository.indexWindow(track.id, samples, start)
        }
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

    private companion object {
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
    }
}
