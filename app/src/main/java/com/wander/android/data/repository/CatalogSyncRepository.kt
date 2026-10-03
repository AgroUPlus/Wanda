package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.dao.TrackLyricsDao
import com.wander.android.core.database.entity.TrackLyricsEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroCatalogApi
import com.wander.android.data.sources.agro.AgroCatalogEntry
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
     *
     * Several pages per sync, up to [MAX_PULL_PAGES]: one page of [PULL_PAGE] every six hours left
     * a device joining a busy server weeks behind it.
     */
    private suspend fun pullCatalogue(): Int {
        var applied = 0
        repeat(MAX_PULL_PAGES) {
            val entries = catalogApi.since(secureStorage.catalogCursor, PULL_PAGE).getOrElse { return applied }
            if (entries.isEmpty()) return applied
            for (entry in entries) applied += applyEntry(entry)

            // The server's `updated_at` is in whole seconds and one batch publish stamps 25 rows
            // with the same one, so a full page can stop part-way through a second. Resuming
            // strictly after it skipped the rest of that second for good; re-reading it is
            // harmless — see `AgroCatalogApi.since`. A page that is all one second has to move
            // on regardless, or it would be read again forever.
            val newest = entries.maxOf { it.updatedAt }
            val full = entries.size >= PULL_PAGE
            secureStorage.catalogCursor =
                if (full && entries.any { it.updatedAt < newest }) newest - 1 else newest
            if (!full) return applied
        }
        return applied
    }

    /** Applies one catalogue entry to every local track holding its audio; how many it improved. */
    private suspend fun applyEntry(entry: AgroCatalogEntry): Int {
        if (entry.model != AudioEmbedder.MODEL_NAME || entry.version != AudioEmbedder.EMBEDDER_VERSION) {
            return 0
        }
        val vectors = unpackHex(entry.embeddingHex, entry.dim) ?: return 0
        var applied = 0
        for (match in recordingIdentity.matchesForEmbedding(vectors, entry.durationMs)) {
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
            if (!entry.lyrics.isNullOrBlank()) saveLyrics(match.trackId, entry.lyrics, entry.lyricsSource)
        }
        return applied
    }

    /** Keeps the catalogue's lyrics where the track has none, or only plain ones and these are synced. */
    private suspend fun saveLyrics(trackId: String, lyrics: String, source: String?) {
        val isSynced = lyrics.startsWith("[")
        val existing = trackLyricsDao.getLyricsForTrack(trackId)
        if (existing != null && !(existing.syncedLyrics.isNullOrBlank() && isSynced)) return
        val plain = if (isSynced) {
            lyrics.lineSequence()
                .map { it.replace(Regex("""^\[\d{2}:\d{2}\.\d{2,3}\]"""), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else {
            lyrics
        }
        trackLyricsDao.saveLyricsWithFts(
            TrackLyricsEntity(
                trackId = trackId,
                plainLyrics = plain,
                syncedLyrics = if (isSynced) lyrics else null,
                source = source?.takeIf { it.isNotBlank() } ?: "Agro",
                viaCatalog = true
            )
        )
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

        /** Entries per catalogue read; the server's own default. */
        const val PULL_PAGE = 200

        /** Pages one sync reads at most, so a catch-up is spread over a few syncs, not one long one. */
        const val MAX_PULL_PAGES = 10

        fun quantiseToHex(vectors: Array<FloatArray>): String =
            CatalogVectorCodec.quantiseToHex(vectors)

        fun unpackHex(hex: String, dim: Int): Array<FloatArray>? =
            CatalogVectorCodec.unpackHex(hex, dim)
    }
}
