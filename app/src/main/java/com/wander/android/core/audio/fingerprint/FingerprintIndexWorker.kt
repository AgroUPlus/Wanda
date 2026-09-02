package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.playback.LIVE_SUFFIX
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.repository.AcousticFeatureRepository
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
    private val musicRepository: MusicRepository
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
        // One named track when the player asked for it, the whole library otherwise. The player's
        // request is a different urgency, not a small sweep: a track you have just started
        // listening to is the one whose missing measurement you might actually notice.
        val requestedId = inputData.getString(KEY_TRACK_ID)
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
            it.id in needsLandmark ||
                it.id in needsCanonical ||
                it.id in needsFeatures ||
                it.id in needsContour
        }
        if (pending.isEmpty()) return@withContext Result.success()

        // Foreground for the duration, and this is what makes the batching honest.
        //
        // A plain worker is killed at around ten minutes, which on a streamed library is a handful
        // of tracks — so the run ended early, deferred the rest, and from the outside looked like
        // an indexer that never finished. As a foreground service it gets to work through the
        // batch, and the person holding the phone can see that it is doing so.
        val batch = pending.take(BATCH_SIZE)
        runCatching { setForeground(notifying(0, batch.size)) }

        for ((index, track) in batch.withIndex()) {
            if (isStopped) return@withContext Result.retry()
            // Updated before each decode rather than after, so the count names the track being
            // worked on rather than the last one finished.
            runCatching { setForeground(notifying(index, batch.size, track.title)) }
            // Marked around the decode and every write that comes off it, in a `finally` so a
            // cancelled worker or a track that fails to decode does not leave the badge spinning.
            progress.started(track.id)
            try {
                val source = audioSourceFor(track) ?: continue
                val samples = decoder.decode(source.first, source.second) ?: continue
                // Guarded now that the list is a union: a track pulled in because it wants a
                // contour must not have its landmarks written a second time.
                if (track.id in needsLandmark) recognitionRepository.index(track, samples)
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

    private fun notifying(done: Int, total: Int, title: String? = null) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.FINGERPRINT,
            // Named for the result rather than the machinery, as the Settings row is:
            // "fingerprinting" means nothing to most people, being able to recognise a song does.
            title = "Measuring your library",
            // The track being worked on, when there is one. A bare "12 of 250" says the phone is
            // busy; the title says what it is busy *with*, which is the difference between a
            // progress bar you trust and one you wonder about.
            text = if (title == null) "$done of $total" else "$done of $total · $title",
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

    companion object {
        private const val NAME = "fingerprint-index"

        /** Names a single track to measure, instead of sweeping the library. */
        internal const val KEY_TRACK_ID = "track_id"

        /**
         * Tracks per run.
         *
         * Was 25, sized for the ten minutes a *plain* worker is given before the platform kills
         * it. The run is a foreground service now, so that ceiling is gone and 25 was simply
         * stopping a library of a thousand tracks after twenty-five of them and waiting for
         * WorkManager's backoff to grant the next handful — which is what "it does ten and stops"
         * looks like from the outside.
         *
         * Still bounded rather than unbounded: `Result.retry()` at the end means a run that is
         * interrupted keeps every track it finished, and a bound is what makes that true.
         */
        private const val BATCH_SIZE = 250

        /**
         * How far ahead to ask which tracks still need a vector. Comfortably more than one batch,
         * so the set covers everything this run could reach without listing a whole library.
         */
        private const val FEATURE_BATCH_LIMIT = 200

        /**
         * Asks for the index to be brought up to date.
         *
         * `KEEP`, so repeated calls — every launch, say — join the run already queued instead of
         * cancelling and restarting it, which on a large library would mean never finishing.
         */
        fun enqueue(context: Context, allowMobileData: Boolean = false) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            // Charging is **not** required, and that is a deliberate reversal.
                            //
                            // It reads like the cautious choice and it quietly gutted the feature.
                            // A library that lives on Navidrome and YouTube Music is only reachable
                            // while the app is in use, and "in use" and "plugged in" are close to
                            // disjoint — so the index never filled, and a recogniser with an empty
                            // index is not a conservative recogniser, it is a broken one.
                            //
                            // The cost was overstated. The decode is `MediaCodec`, which is the
                            // same hardware path that plays the song, on one track at a time,
                            // capped at [BATCH_SIZE] per run with the rest deferred to the next.
                            // What is actually expensive here is the network, and that is what the
                            // remaining two constraints are for.
                            .setRequiresBatteryNotLow(true)
                            // A network constraint at all, because this now reads audio. It used
                            // to declare none on the grounds that it touched none, which stopped
                            // being true the moment streamed tracks became indexable.
                            //
                            // Unmetered unless the user has said otherwise: measuring a streamed
                            // library reads about a minute per track, which is free on Wi-Fi and
                            // is somebody's data plan anywhere else.
                            .setRequiredNetworkType(
                                if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
                            )
                            .build()
                    )
                    .build()
            )
        }

        /**
         * Measures one track now, because it is the one playing.
         *
         * Unconstrained, and deliberately so: this is a single track, roughly a minute of audio,
         * for a song the user is listening to at this moment. The Wi-Fi and battery constraints on
         * the sweep exist because it walks a thousand tracks — they are a rule about bulk, not a
         * rule about fingerprinting.
         *
         * Keyed per track so it neither cancels the sweep nor is cancelled by it, and `KEEP` so a
         * track being re-observed does not restart its own measurement.
         */
        fun enqueueFor(context: Context, trackId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$NAME:$trackId",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                    .setInputData(workDataOf(KEY_TRACK_ID to trackId))
                    .build()
            )
        }

        /**
         * Runs it now, ignoring every constraint, for a user who asked from Settings.
         *
         * The KDoc used to say "constraints and all" beside a builder that sets none. Asking
         * explicitly is the one case where overriding them is right — the user is looking at the
         * screen and has said to go — so the behaviour stays and the description is corrected.
         */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FingerprintIndexWorker>().build()
            )
        }
    }
}
