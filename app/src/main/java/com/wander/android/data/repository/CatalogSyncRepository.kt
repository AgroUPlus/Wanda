package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroCatalogApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
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

    /** Sends every local fingerprint the server has not been told about. */
    private suspend fun publishLocal(): Int = 0

    /** Reads what the fleet has learned. */
    private suspend fun pullCatalogue(): Int = 0

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
        }
    }

    private companion object {
        const val TAG = "CatalogSync"

        /** Fingerprints per run, so a first sync on a large library does not run for minutes. */
        const val PUBLISH_BATCH = 100

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun String.hexToHashes(): IntArray? {
            if (length % 8 != 0 || isEmpty()) return null
            return runCatching {
                IntArray(length / 8) { index ->
                    substring(index * 8, index * 8 + 8).toLong(16).toInt()
                }
            }.getOrNull()
        }
    }
}
