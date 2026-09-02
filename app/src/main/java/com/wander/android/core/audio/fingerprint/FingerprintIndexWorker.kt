package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.playback.LIVE_SUFFIX
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.repository.AcousticFeatureRepository
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
    private val musicRepository: MusicRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // Both fingerprints come off one decode. They answer different questions and share
        // nothing but the samples, and decoding is by far the expensive part — doing it twice for
        // one file would double the cost of indexing a library to no purpose.
        val needsLandmark = recognitionRepository.tracksNeedingIndex()
        val needsCanonical = recordingIdentity
            .needingIndex(needsLandmark.map { it.id })
            .toSet()
        // Four measurements, one decode. The acoustic vector answers a third question again —
        // not "what is this" but "what does it sound like", and the melody contour asks a fourth,
        // "how does it go". Each on its own would be another full pass over the library.
        val needsFeatures = acousticFeatures.needingMeasurement(FEATURE_BATCH_LIMIT)
        val needsContour = melodySearch.needingIndex(needsLandmark.map { it.id })
        val pending = needsLandmark
        if (pending.isEmpty()) return@withContext Result.success()

        for (track in pending.take(BATCH_SIZE)) {
            if (isStopped) return@withContext Result.retry()
            // Marked around the decode and every write that comes off it, in a `finally` so a
            // cancelled worker or a track that fails to decode does not leave the badge spinning.
            progress.started(track.id)
            try {
                val source = audioSourceFor(track) ?: continue
                val samples = decoder.decode(source.first, source.second) ?: continue
                recognitionRepository.index(track, samples)
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

        /**
         * Tracks per run. Small enough to finish inside the ten minutes a plain worker is given,
         * with room for the slowest files in a collection.
         */
        private const val BATCH_SIZE = 25

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
