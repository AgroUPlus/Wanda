package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroCatalogApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Trades fingerprints with an Agro server, in both directions.
 *
 * Strictly an addition. Identification works with no server configured — the fingerprinter and its
 * index are local, and a device on its own can still tell that two of its files are one recording.
 * What syncing buys is that a device does not have to have *heard* a recording to know what it is:
 * another device on the account worked it out, and the canonical metadata came with it.
 *
 * Publishing sends fingerprints and the tags this device happens to hold. It sends nothing about
 * listening — no plays, no timestamps, no history. The catalogue is a set of facts about audio.
 */
@Singleton
internal class CatalogSyncRepository @Inject constructor(
    private val catalogApi: AgroCatalogApi,
    private val musicRepository: MusicRepository,
    private val canonicalMetadata: CanonicalMetadataRepository,
    private val recordingIdentity: RecordingIdentityRepository,
    private val embeddingDao: TrackEmbeddingDao,
    private val secureStorage: SecureStorage
) {

    /**
     * Pushes what this device has fingerprinted, then pulls what it has not seen.
     *
     * Push first, so a device that has just indexed something contributes it before asking what
     * everyone else knows — otherwise two devices indexing the same library both wait for the
     * other to publish it.
     */
    suspend fun sync(): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        if (secureStorage.agroServerUrl.isBlank()) {
            return@withContext Result.success(SyncOutcome.NOT_CONFIGURED)
        }
        // One switch, both directions. Pulling without publishing would be taking the benefit of
        // everyone else's disclosure while making none of your own, and the catalogue only holds
        // anything because people contribute to it.
        if (!secureStorage.agroCatalogTrade) {
            return@withContext Result.success(SyncOutcome.NOT_TRADING)
        }
        runCatching {
            val published = publishLocal()
            val received = pullCatalogue()
            // Unconditional, and after the pull. A library refetch since the last run will have
            // restored each source's own metadata over corrections applied then, so this is a
            // repair pass as much as it is the delivery of what arrived just now.
            val corrected = canonicalMetadata.applyToLibrary()
            SyncOutcome(published = published, received = received, corrected = corrected)
        }.onFailure { error ->
            // Never surfaced as a failure the user has to act on: the catalogue is an
            // optimisation, and the app identifies music perfectly well without having reached it.
            Log.w(TAG, "Catalogue sync did not complete: ${error.message}")
        }
    }

    /**
     * Sends every local embedding the server has not been told about.
     *
     * The cursor advances only past embeddings that actually landed, and a failure stops the batch
     * rather than skipping it: the next run should retry what did not go, not step over it.
     *
     * A `local:` source id is never sent. It is a filesystem path from this device and the
     * catalogue's source list is readable by every account on the server. The server drops them
     * too — this is the half that means one was never transmitted in the first place.
     */
    private suspend fun publishLocal(): Int {
        val lastPublished = secureStorage.catalogLastPublishedAt
        val mine = embeddingDao.computedSince(
            after = lastPublished,
            model = AudioEmbedder.MODEL_NAME,
            version = AudioEmbedder.EMBEDDER_VERSION,
            limit = PUBLISH_BATCH
        )
        if (mine.isEmpty()) return 0

        var sent = 0
        var newest = lastPublished
        for (embedding in mine) {
            val track = musicRepository.trackById(embedding.trackId) ?: continue
            if (track.durationMs <= 0L) continue

            val published = catalogApi.publish(
                embeddingHex = quantiseToHex(AudioEmbedder.unpack(embedding.vector)),
                dim = embedding.dim,
                model = embedding.model,
                version = embedding.version,
                durationMs = track.durationMs,
                title = track.title,
                artist = track.artist,
                album = track.album,
                sourceUri = embedding.trackId.takeUnless { it.startsWith(LOCAL_PREFIX) }
            )
            if (published.isFailure) break
            sent++
            newest = maxOf(newest, embedding.computedAt)
        }
        secureStorage.catalogLastPublishedAt = newest
        return sent
    }

    /**
     * Reads what the fleet has learned and records whatever names a recording this device holds.
     *
     * "Records" rather than applies: [CanonicalMetadataRepository] decides which of the catalogue's
     * values actually improve on what the source gave, and [sync] applies them afterwards.
     *
     * An entry is matched locally rather than trusted. The server says these vectors are one
     * recording; whether they are *this device's* recording is a question only this device's own
     * embeddings can answer, at the same thresholds the server used.
     *
     * Entries for audio this device has never heard are counted and dropped. Keeping the fleet's
     * whole catalogue on every phone would make a shared server's size everyone's problem, and the
     * entry is still there to be re-read on the day the audio arrives.
     */
    private suspend fun pullCatalogue(): Int {
        val cursor = secureStorage.catalogCursor
        val entries = catalogApi.since(cursor).getOrElse { return 0 }
        if (entries.isEmpty()) return 0

        var applied = 0
        for (entry in entries) {
            // A vector from another embedder is a different alphabet. Comparing across them would
            // produce a confident number that means nothing.
            if (entry.model != AudioEmbedder.MODEL_NAME ||
                entry.version != AudioEmbedder.EMBEDDER_VERSION
            ) {
                continue
            }
            val vectors = unpackHex(entry.embeddingHex, entry.dim) ?: continue
            val matches = recordingIdentity.matchesForEmbedding(vectors, entry.durationMs)
            for (match in matches) {
                if (canonicalMetadata.record(
                        trackId = match.trackId,
                        recordingId = entry.recordingId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album
                    )
                ) {
                    applied++
                }
            }
        }
        secureStorage.catalogCursor = entries.maxOf { it.updatedAt }
        return applied
    }

    /** What one sync did. */
    data class SyncOutcome(
        val published: Int,
        /** Catalogue entries that improved on a local row's metadata. */
        val received: Int,
        /** Rows whose displayed metadata was written this run, corrections and repairs alike. */
        val corrected: Int = 0
    ) {
        companion object {
            /** No server configured, which is a supported way to run and not a failure. */
            val NOT_CONFIGURED = SyncOutcome(0, 0)

            /** The switch is off. Also not a failure — it is the default. */
            val NOT_TRADING = SyncOutcome(0, 0)
        }
    }

    /**
     * `internal` rather than private for the codec below: the claim that int8 does not cost a match
     * is the one thing here that is not obvious by reading, and it is only checkable by round
     * -tripping real vectors through it.
     */
    internal companion object {
        const val TAG = "CatalogSync"

        /**
         * Recordings per run, so a first sync on a large library does not run for minutes.
         *
         * Deliberately smaller than the old fingerprint batch. An embedding is about a kilobyte per
         * second of audio even at int8, so twenty tracks is already megabytes on the wire.
         */
        const val PUBLISH_BATCH = 20

        /** Source ids that name a file on this device rather than a thing others could hold. */
        const val LOCAL_PREFIX = "local:"

        /**
         * The scale the wire format uses. `AudioEmbedder` L2-normalises every vector, so each value
         * already lies in [-1, 1] and a fixed 127x scale is the whole quantisation.
         */
        const val INT8_SCALE = 127f

        /**
         * Packs vectors to hex int8 — a quarter of float32, for a change in cosine similarity that
         * does not reach the second decimal place.
         *
         * Storage on the device stays float32. This is the wire only: at ~1 KB per second of audio,
         * float32 puts a three-minute track past the server's request cap on its own.
         */
        fun quantiseToHex(vectors: Array<FloatArray>): String {
            val out = StringBuilder(vectors.sumOf { it.size } * 2)
            for (vector in vectors) {
                for (value in vector) {
                    val q = (value * INT8_SCALE).roundToInt().coerceIn(-127, 127)
                    out.append(HEX[(q shr 4) and 0xF]).append(HEX[q and 0xF])
                }
            }
            return out.toString()
        }

        /** Inverse of [quantiseToHex]. Null when the blob is not a whole number of vectors. */
        fun unpackHex(hex: String, dim: Int): Array<FloatArray>? {
            if (dim <= 0 || hex.length % 2 != 0) return null
            val bytes = hex.length / 2
            if (bytes == 0 || bytes % dim != 0) return null
            return runCatching {
                Array(bytes / dim) { segment ->
                    FloatArray(dim) { d ->
                        val at = (segment * dim + d) * 2
                        hex.substring(at, at + 2).toInt(16).toByte() / INT8_SCALE
                    }
                }
            }.getOrNull()
        }

        private const val HEX = "0123456789abcdef"
    }
}
