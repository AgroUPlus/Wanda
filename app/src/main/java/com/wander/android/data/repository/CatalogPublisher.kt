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
     * Sends local embeddings the server has not been told about, up to [MAX_PER_RUN] per call.
     *
     * The cursor advances past an embedding once it has been sent *or* deliberately skipped (its
     * track has left the library). It used to advance only past sent ones, so a page made up
     * entirely of skipped rows left it where it was — and every later sync read the same page and
     * stopped there, publishing nothing ever again.
     */
    suspend fun publishLocal(): Int {
        var sent = 0
        while (sent < MAX_PER_RUN) {
            val page = embeddingDao.computedSince(
                after = secureStorage.catalogLastPublishedAt,
                afterTrackId = secureStorage.catalogLastPublishedTrack,
                model = AudioEmbedder.MODEL_NAME,
                version = AudioEmbedder.EMBEDDER_VERSION,
                limit = AgroCatalogApi.MAX_BATCH
            )
            if (page.isEmpty()) break
            val publications = page.mapNotNull { embedding -> publicationFor(embedding)?.let { embedding to it } }
            val delivered = when {
                publications.isEmpty() -> true
                catalogApi.supportsBatch -> catalogApi.publishAll(publications.map { it.second })
                    .onSuccess { outcomes ->
                        outcomes.mapNotNull { it.error }.forEach { Log.w(TAG, "The catalogue would not take one recording: $it") }
                    }
                    .isSuccess
                else -> publishOneAtATime(publications)
            }
            if (!delivered) break
            sent += publications.size
            markPublished(page.last())
        }
        return sent
    }

    /** What the catalogue is told about one embedding, or null when its track has left the library. */
    private suspend fun publicationFor(embedding: TrackEmbeddingEntity): AgroCatalogApi.Publication? {
        val track = musicRepository.trackById(embedding.trackId) ?: return null
        // A backend that left the duration at zero still has audio of a known length: the
        // fingerprint is half a second per segment of what actually decoded. Skipping these used
        // to keep most of a YouTube Music library out of the catalogue altogether.
        val durationMs = track.durationMs.takeIf { it > 0L }
            ?: (embedding.vector.size / embedding.dim).toLong() * EmbeddingScorer.SEGMENT_HOP_MS

        val lyricsEntity = trackLyricsDao.findLyricsForTrackOrMetadata(track.id, track.title, track.artist)
            ?: trackLyricsDao.getLyricsForTrack(embedding.trackId)
        val lyricsPayload = lyricsEntity?.syncedLyrics
            ?: lyricsEntity?.plainLyrics?.takeIf { it.isNotBlank() }

        return AgroCatalogApi.Publication(
            embeddingHex = CatalogVectorCodec.quantiseToHex(AudioEmbedder.unpack(embedding.vector)),
            dim = embedding.dim,
            model = embedding.model,
            version = embedding.version,
            durationMs = durationMs,
            title = track.title,
            artist = track.artist,
            album = track.album,
            sourceUri = embedding.trackId.takeUnless { it.startsWith(LOCAL_PREFIX) },
            lyrics = lyricsPayload,
            lyricsSource = lyricsEntity?.source?.takeIf { lyricsPayload != null }
        )
    }

    /** For a server without the batch mutation. True when every one of them was taken. */
    private suspend fun publishOneAtATime(
        publications: List<Pair<TrackEmbeddingEntity, AgroCatalogApi.Publication>>
    ): Boolean {
        for ((embedding, publication) in publications) {
            if (catalogApi.publish(publication).isFailure) return false
            // Per entry, so a failure part-way through does not resend the ones before it.
            markPublished(embedding)
        }
        return true
    }

    private fun markPublished(embedding: TrackEmbeddingEntity) {
        secureStorage.catalogLastPublishedAt = embedding.computedAt
        secureStorage.catalogLastPublishedTrack = embedding.trackId
    }

    companion object {
        private const val TAG = "CatalogPublisher"

        /**
         * Recordings one run may publish. Under the server's 300 per five minutes, so a run is
         * never refused part-way; it used to be 20, which put a few thousand tracks a month away.
         */
        const val MAX_PER_RUN = 250
        const val LOCAL_PREFIX = "local:"
    }
}
