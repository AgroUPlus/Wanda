package com.wander.android.core.audio.fingerprint

import com.wander.android.core.database.dao.TrackAttemptDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.playback.LIVE_SUFFIX
import com.wander.android.data.model.isOneShotTrackId
import com.wander.android.data.repository.AcousticFeatureRepository
import com.wander.android.data.repository.EmbeddingRepository
import com.wander.android.data.repository.MelodySearchRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.RecordingIdentityRepository
import com.wander.android.data.repository.RecordingLinkRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles audio decoding, feature extraction, contour indexing, and neural embedding for a single track.
 */
@Singleton
class FingerprintTrackProcessor @Inject constructor(
    private val acousticFeatures: AcousticFeatureRepository,
    private val melodySearch: MelodySearchRepository,
    private val embeddingSearch: EmbeddingRepository,
    private val recordingIdentity: RecordingIdentityRepository,
    private val recordingLinks: RecordingLinkRepository,
    private val decoder: PcmDecoder,
    private val progress: FingerprintProgress,
    private val musicRepository: MusicRepository,
    private val trackAttemptDao: TrackAttemptDao
) {
    /**
     * Decodes and indexes the requested measurements for a single track.
     */
    suspend fun processTrack(
        track: TrackEntity,
        needsFeatures: Boolean,
        needsContour: Boolean,
        needsEmbedding: Boolean
    ) {
        progress.started(track.id)
        try {
            val source = audioSourceFor(track)
            if (source == null) {
                progress.couldNotReach(track.id)
                trackAttemptDao.recordAttempt(track.id, System.currentTimeMillis())
                return
            }
            // Only the embedding wants the whole track. Features and contours are measured
            // over the first minute, so reading twenty for a track that needs one of those
            // and not an embedding is nineteen minutes of decode, and of memory, for samples
            // that are then thrown away.
            val wantsWholeTrack = needsEmbedding
            val samples = decoder.decode(
                source.first,
                source.second,
                maxSeconds = if (wantsWholeTrack) {
                    EMBEDDING_MAX_SECONDS
                } else {
                    PcmDecoder.DEFAULT_MAX_SECONDS
                }
            )
            if (samples == null) {
                progress.couldNotReach(track.id)
                trackAttemptDao.recordAttempt(track.id, System.currentTimeMillis())
                return
            }
            if (track.attempts > 0) {
                trackAttemptDao.clearAttempts(track.id)
            }
            // The head of the same decode. Features and contours were measured over the first
            // minute and their stored `version` says so, so handing them the whole track now
            // would silently change every number they have ever written without anything
            // marking the change. The embedding is the one that wanted the rest of the song.
            val head = samples.headSeconds(PcmDecoder.DEFAULT_MAX_SECONDS)
            if (needsFeatures) acousticFeatures.measure(track.id, head)
            if (needsContour) melodySearch.index(track.id, head)
            if (needsEmbedding) {
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

    /**
     * Where to read a minute of this track's audio, and what to send with the request.
     */
    private suspend fun audioSourceFor(track: TrackEntity): Pair<String, Map<String, String>>? {
        track.localFilePath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { return it to emptyMap() }

        if (track.isLive || isOneShotTrackId(track.id)) return null
        val stream = musicRepository.getStreamInfo(track.id).getOrNull() ?: return null
        if (stream.uri.endsWith(LIVE_SUFFIX)) return null
        return stream.uri to stream.headers
    }

    companion object {
        const val EMBEDDING_MAX_SECONDS = 12 * 60
    }
}

/**
 * The first [seconds] of a decoded clip, or the clip itself when it is already shorter.
 */
private fun FloatArray.headSeconds(seconds: Int): FloatArray {
    val wanted = seconds * AudioFormat.SAMPLE_RATE
    return if (size <= wanted) this else copyOf(wanted)
}
