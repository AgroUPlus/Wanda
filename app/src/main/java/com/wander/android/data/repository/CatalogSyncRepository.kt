package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroCatalogApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    musicRepository: MusicRepository,
    private val canonicalMetadata: CanonicalMetadataRepository,
    private val recordingIdentity: RecordingIdentityRepository,
    private val embeddingDao: TrackEmbeddingDao,
    private val trackLyricsDao: TrackLyricsDao,
    private val secureStorage: SecureStorage,
    private val publisher: CatalogPublisher
) {

    constructor(
        catalogApi: AgroCatalogApi,
        musicRepository: MusicRepository,
        canonicalMetadata: CanonicalMetadataRepository,
        recordingIdentity: RecordingIdentityRepository,
        embeddingDao: TrackEmbeddingDao,
        trackLyricsDao: TrackLyricsDao,
        secureStorage: SecureStorage
    ) : this(
        catalogApi = catalogApi,
        musicRepository = musicRepository,
        canonicalMetadata = canonicalMetadata,
        recordingIdentity = recordingIdentity,
        embeddingDao = embeddingDao,
        trackLyricsDao = trackLyricsDao,
        secureStorage = secureStorage,
        publisher = CatalogPublisher(catalogApi, musicRepository, embeddingDao, trackLyricsDao, secureStorage)
    )

    /**
     * How many fingerprints this device has contributed, and how many lyrics it has been given.
     */
    val fingerprintsShared: Flow<Int>
        get() = embeddingDao.publishedCountFlow(
            model = AudioEmbedder.MODEL_NAME,
            version = AudioEmbedder.EMBEDDER_VERSION,
            publishedThrough = secureStorage.catalogLastPublishedAt
        )

    val lyricsReceived: Flow<Int> get() = trackLyricsDao.countFromCatalogueFlow()

    /**
     * Pushes what this device has fingerprinted, then pulls what it has not seen.
     */
    suspend fun sync(): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        if (secureStorage.agroServerUrl.isBlank()) {
            return@withContext Result.success(SyncOutcome.NOT_CONFIGURED)
        }
        if (!secureStorage.agroCatalogTrade) {
            return@withContext Result.success(SyncOutcome.NOT_TRADING)
        }
        runCatching {
            val published = publisher.publishLocal()
            val received = pullCatalogue()
            val corrected = canonicalMetadata.applyToLibrary()
            SyncOutcome(published = published, received = received, corrected = corrected)
        }.onFailure { error ->
            Log.w(TAG, "Catalogue sync did not complete: ${error.message}")
        }
    }

    /**
     * Reads what the fleet has learned and records whatever names a recording this device holds.
     */
    private suspend fun pullCatalogue(): Int {
        val cursor = secureStorage.catalogCursor
        val entries = catalogApi.since(cursor).getOrElse { return 0 }
        if (entries.isEmpty()) return 0

        var applied = 0
        for (entry in entries) {
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

                if (!entry.lyrics.isNullOrBlank()) {
                    val existing = trackLyricsDao.getLyricsForTrack(match.trackId)
                    if (existing == null || (existing.syncedLyrics.isNullOrBlank() && entry.lyrics.startsWith("["))) {
                        val isSynced = entry.lyrics.startsWith("[")
                        val plain = if (isSynced) {
                            entry.lyrics.lineSequence()
                                .map { it.replace(Regex("""^\[\d{2}:\d{2}\.\d{2,3}\]"""), "").trim() }
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        } else {
                            entry.lyrics
                        }
                        trackLyricsDao.saveLyricsWithFts(
                            TrackLyricsEntity(
                                trackId = match.trackId,
                                plainLyrics = plain,
                                syncedLyrics = if (isSynced) entry.lyrics else null,
                                source = entry.lyricsSource?.takeIf { it.isNotBlank() } ?: "Agro",
                                viaCatalog = true
                            )
                        )
                    }
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

    internal companion object {
        const val TAG = "CatalogSync"

        fun quantiseToHex(vectors: Array<FloatArray>): String =
            CatalogVectorCodec.quantiseToHex(vectors)

        fun unpackHex(hex: String, dim: Int): Array<FloatArray>? =
            CatalogVectorCodec.unpackHex(hex, dim)
    }
}
