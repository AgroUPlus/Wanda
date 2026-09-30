package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackEmbeddingEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroCatalogApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes locally generated audio embeddings and lyrics to the Agro catalogue.
 */
@Singleton
internal class CatalogPublisher @Inject constructor(
    private val catalogApi: AgroCatalogApi,
    private val musicRepository: MusicRepository,
    private val embeddingDao: TrackEmbeddingDao,
    private val trackLyricsDao: TrackLyricsDao,
    private val secureStorage: SecureStorage
) {
    /**
     * Sends every local embedding the server has not been told about.
     */
    suspend fun publishLocal(): Int {
        val lastPublished = secureStorage.catalogLastPublishedAt
        val mine = embeddingDao.computedSince(
            after = lastPublished,
            model = AudioEmbedder.MODEL_NAME,
            version = AudioEmbedder.EMBEDDER_VERSION,
            limit = PUBLISH_BATCH
        )
        if (mine.isEmpty()) return 0

        val pending = mine.mapNotNull { embedding ->
            val track = musicRepository.trackById(embedding.trackId) ?: return@mapNotNull null
            if (track.durationMs <= 0L) return@mapNotNull null

            val lyricsEntity = trackLyricsDao.findLyricsForTrackOrMetadata(track.id, track.title, track.artist)
                ?: trackLyricsDao.getLyricsForTrack(embedding.trackId)
            val lyricsPayload = lyricsEntity?.syncedLyrics
                ?: lyricsEntity?.plainLyrics?.takeIf { it.isNotBlank() }

            embedding to AgroCatalogApi.Publication(
                embeddingHex = CatalogVectorCodec.quantiseToHex(AudioEmbedder.unpack(embedding.vector)),
                dim = embedding.dim,
                model = embedding.model,
                version = embedding.version,
                durationMs = track.durationMs,
                title = track.title,
                artist = track.artist,
                album = track.album,
                sourceUri = embedding.trackId.takeUnless { it.startsWith(LOCAL_PREFIX) },
                lyrics = lyricsPayload,
                lyricsSource = lyricsEntity?.source?.takeIf { lyricsPayload != null }
            )
        }
        if (pending.isEmpty()) return 0

        return if (catalogApi.supportsBatch) {
            publishInBatches(pending)
        } else {
            publishOneAtATime(pending)
        }
    }

    private suspend fun publishInBatches(pending: List<Pair<TrackEmbeddingEntity, AgroCatalogApi.Publication>>): Int {
        var sent = 0
        var newest = secureStorage.catalogLastPublishedAt
        for (chunk in pending.chunked(AgroCatalogApi.MAX_BATCH)) {
            val outcomes = catalogApi.publishAll(chunk.map { it.second }).getOrElse { break }
            chunk.forEachIndexed { index, (embedding, _) ->
                val outcome = outcomes.getOrNull(index)
                if (outcome != null && outcome.error != null) {
                    Log.w(TAG, "The catalogue would not take one recording: ${outcome.error}")
                }
                sent++
                newest = maxOf(newest, embedding.computedAt)
            }
        }
        secureStorage.catalogLastPublishedAt = newest
        return sent
    }

    private suspend fun publishOneAtATime(pending: List<Pair<TrackEmbeddingEntity, AgroCatalogApi.Publication>>): Int {
        var sent = 0
        var newest = secureStorage.catalogLastPublishedAt
        for ((embedding, publication) in pending) {
            if (catalogApi.publish(publication).isFailure) break
            sent++
            newest = maxOf(newest, embedding.computedAt)
        }
        secureStorage.catalogLastPublishedAt = newest
        return sent
    }

    companion object {
        private const val TAG = "CatalogPublisher"
        const val PUBLISH_BATCH = 20
        const val LOCAL_PREFIX = "local:"
    }
}
