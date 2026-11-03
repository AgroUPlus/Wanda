package com.wander.android.core.backup

import com.wander.android.core.audio.fingerprint.AudioEmbedder
import com.wander.android.core.database.dao.CanonicalMetadataDao
import com.wander.android.core.database.dao.JamRecapDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.dao.TrackFeatureDao
import com.wander.android.data.repository.AcousticFeatureRepository
import com.wander.android.data.repository.sharedplaylist.SharedSyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Reads and writes the sections in [BackupExtras.kt] — kept out of [BackupContentsIO] so each
 * stays one readable file. Restores merge and never delete, as every other section does.
 */
@Singleton
internal class BackupExtrasIO @Inject constructor(
    private val canonicalDao: CanonicalMetadataDao,
    private val sharedDao: SharedPlaylistDao,
    private val jamRecapDao: JamRecapDao,
    private val featureDao: TrackFeatureDao,
    private val embeddingDao: TrackEmbeddingDao,
    private val trackDao: TrackDao
) {

    /** [document] with the extra sections in [sections] filled in from this device. */
    suspend fun collectInto(document: BackupDocument, sections: Set<BackupSection>): BackupDocument {
        val on = { section: BackupSection -> section in sections }
        return document.copy(
            canonical = if (on(BackupSection.METADATA)) {
                canonicalDao.getAllOnce().sortedBy { it.trackId }.map { it.toBackup() }
            } else {
                emptyList()
            },
            sharedPlaylists = if (on(BackupSection.SHARED)) {
                sharedDao.all().sortedBy { it.agroId }.map { it.toBackup(sharedDao.items(it.agroId)) }
            } else {
                emptyList()
            },
            jamRecaps = if (on(BackupSection.SHARED)) {
                jamRecapDao.observeAll().first().sortedBy { it.id }.map { it.toBackup() }
            } else {
                emptyList()
            },
            features = if (on(BackupSection.FEATURES)) {
                featureDao.getAll(AcousticFeatureRepository.EXTRACTOR_VERSION).sortedBy {
                    it.trackId
                }.map { it.toBackup() }
            } else {
                emptyList()
            },
            embeddings = if (on(BackupSection.EMBEDDINGS)) embeddingsWithinBudget() else emptyList()
        )
    }

    /**
     * Current-model fingerprints, most-played tracks first, until [EMBEDDING_BUDGET_BYTES] is
     * spent.
     *
     * Bounded because the whole document is held in memory — several times over while it is
     * encoded, hashed and read back — and a large library's fingerprints run to tens of megabytes.
     * The budget keeps the export inside an ordinary heap; whatever does not fit is simply indexed
     * again on the restored device, as it would have been with no backup at all. Played tracks go
     * first because they are the ones a recognition or a duplicate scan is likeliest to need.
     */
    private suspend fun embeddingsWithinBudget(): List<BackupEmbedding> {
        val ids = trackDao.tracksWithUserState().sortedByDescending { it.playCount }.map { it.id }
        val chosen = mutableListOf<BackupEmbedding>()
        var spent = 0L
        for (batch in ids.chunked(EMBEDDING_BATCH)) {
            val rows = embeddingDao.getForTracks(batch, AudioEmbedder.MODEL_NAME, AudioEmbedder.EMBEDDER_VERSION)
            for (row in rows) {
                val size = row.vector.size + (row.centroid?.size ?: 0)
                if (spent + size > EMBEDDING_BUDGET_BYTES) return chosen.sortedBy { it.trackId }
                spent += size
                chosen += row.toBackup()
            }
        }
        return chosen.sortedBy { it.trackId }
    }

    /** Writes the extra sections; called inside [BackupContentsIO.restore]'s transaction. */
    suspend fun restore(document: BackupDocument) {
        // The newer correction wins, as the catalogue itself would answer.
        document.canonical.forEach { row ->
            val here = canonicalDao.forTrack(row.trackId)
            if (here == null || here.updatedAt < row.updatedAt) canonicalDao.upsert(listOf(row.toEntity()))
        }
        // A copy this device already holds is live and at least as current; only missing ones
        // come back, marked out of sync so the next sync checks them against the server.
        document.sharedPlaylists.forEach { playlist ->
            if (sharedDao.get(playlist.agroId) == null) {
                sharedDao.upsert(playlist.toEntity(SharedSyncState.OUT_OF_SYNC.name))
                sharedDao.insertItems(playlist.itemEntities())
            }
        }
        // The server's list replaces this table on the next sync; until then a restored recap is
        // there to read offline. One already here is the same recap.
        val recapsHere = jamRecapDao.observeAll().first().mapTo(mutableSetOf()) { it.id }
        jamRecapDao.insertAll(document.jamRecaps.filterNot { it.id in recapsHere }.map { it.toEntity() })
        // A measurement from another extractor or model is a different alphabet; it is skipped
        // rather than mixed in, and the indexer measures that track again.
        document.features.filter { it.version == AcousticFeatureRepository.EXTRACTOR_VERSION }.forEach { row ->
            val here = featureDao.get(row.trackId)
            if (here == null || here.version != row.version || here.measuredAt < row.measuredAt) {
                featureDao.upsert(row.toEntity())
            }
        }
        document.embeddings
            .filter { it.model == AudioEmbedder.MODEL_NAME && it.version == AudioEmbedder.EMBEDDER_VERSION }
            .forEach { row ->
                val here = embeddingDao.getForTrack(row.trackId, row.model, row.version)
                if (here == null || here.computedAt < row.computedAt) embeddingDao.upsert(row.toEntity())
            }
    }

    private companion object {
        /** Raw fingerprint bytes per backup: roughly 270 three-minute tracks. */
        const val EMBEDDING_BUDGET_BYTES = 12L * 1024 * 1024
        const val EMBEDDING_BATCH = 200
    }
}
