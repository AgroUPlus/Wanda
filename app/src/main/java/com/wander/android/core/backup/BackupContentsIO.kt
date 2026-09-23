package com.wander.android.core.backup

import androidx.room.withTransaction
import com.wander.android.core.database.WanderDatabase
import com.wander.android.core.database.dao.EpisodeProgressDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.RecordingLinkDao
import com.wander.android.core.database.dao.RecordingSplitDao
import com.wander.android.core.database.dao.ReplayRecapDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the chosen sections out of this device, and writes a verified backup's sections back in.
 *
 * Kept apart from [SettingsBackupStore], which owns the file and the crypto: this owns what the
 * sections *mean* on each side.
 */
@Singleton
internal class BackupContentsIO @Inject constructor(
    private val database: WanderDatabase,
    private val secureStorage: SecureStorage,
    private val historyDao: HistoryDao,
    private val recapDao: ReplayRecapDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val splitDao: RecordingSplitDao,
    private val linkDao: RecordingLinkDao,
    private val episodeDao: EpisodeProgressDao
) {

    suspend fun collect(sections: Set<BackupSection>): BackupDocument {
        // Sorted so the same device exports the same bytes twice — easier to compare two backups.
        val stored = secureStorage.exportAll().toBackupEntries().toSortedMap()
        val (accounts, settings) = stored.entries.partition { secureStorage.isAccountKey(it.key) }
        val on = { section: BackupSection -> section in sections }

        return BackupDocument(
            entries = if (on(BackupSection.SETTINGS)) settings.associate { it.toPair() } else emptyMap(),
            accounts = if (on(BackupSection.ACCOUNTS)) accounts.associate { it.toPair() } else emptyMap(),
            history = if (on(BackupSection.HISTORY)) {
                historyDao.allPlays().map { BackupPlay(it.trackId, it.playedAt) }
            } else {
                emptyList()
            },
            recaps = if (on(BackupSection.HISTORY)) recapDao.observeAll().first().map { it.toBackup() } else emptyList(),
            tracks = tracksFor(sections),
            playlists = if (on(BackupSection.LIBRARY)) playlistDao.getAllPlaylists().map { it.toBackup() } else emptyList(),
            splits = if (on(BackupSection.MERGES)) splitDao.getAllOnce().map { it.toBackup() } else emptyList(),
            links = if (on(BackupSection.MERGES)) linkDao.getAllOnce().map { it.toBackup() } else emptyList(),
            episodes = if (on(BackupSection.EPISODES)) episodeDao.getAll().map { it.toBackup() } else emptyList()
        ).let { it.copy(manifest = it.sectionDigests(BackupJson, sections)) }
    }

    /**
     * The tracks the chosen sections need: with LIBRARY, every track with user state; with either
     * LIBRARY or HISTORY, every track a play or playlist points at — so what is restored resolves
     * to a title on a fresh install before any library sync.
     *
     * Without LIBRARY the tracks travel only so plays can be shown, stripped of likes, library
     * membership and counts: leaving the library out has to mean none of it comes back.
     */
    private suspend fun tracksFor(sections: Set<BackupSection>): List<BackupTrack> {
        val library = BackupSection.LIBRARY in sections
        val history = BackupSection.HISTORY in sections
        if (!library && !history) return emptyList()

        val withState = if (library) trackDao.tracksWithUserState() else emptyList()
        val known = withState.mapTo(mutableSetOf()) { it.id }
        val referenced = buildList {
            if (history) addAll(historyDao.allPlays().map { it.trackId })
            if (library) addAll(playlistDao.getAllPlaylists().flatMap { it.trackIds.split(',') })
        }.filter { it.isNotBlank() && it !in known }.distinct()
        val extra = referenced.chunked(QUERY_BATCH).flatMap { trackDao.getTracksByIds(it) }
            .map { if (library) it else it.copy(isLiked = false, isLibrary = false, playCount = 0, lastPlayedTimestamp = null) }
        return (withState + extra).sortedBy { it.id }.map { it.toBackup() }
    }

    /**
     * Writes every section [document] carries in one transaction, then the preferences.
     *
     * One transaction, so a failure part-way leaves the database exactly as it was rather than
     * half-restored. Preferences go last because `SharedPreferences` cannot join that transaction:
     * if the database write fails they are never touched.
     *
     * Everything here merges and never deletes, except the preferences of a chosen section — see
     * [SecureStorage.importAll].
     */
    suspend fun restore(document: BackupDocument): BackupContents {
        val sections = document.includedSections
        var tracksRestored = 0
        database.withTransaction {
            tracksRestored = restoreTracks(document.tracks)
            restoreHistory(document.history)
            // Replaced by year: two recaps of one year describe the same year, and the restored
            // one is at least as complete as whatever this device had.
            document.recaps.forEach { recapDao.save(it.toEntity()) }
            restorePlaylists(document.playlists)
            splitDao.upsert(document.splits.map { it.toSplit() })
            linkDao.upsert(document.links.map { it.toLink() })
            document.episodes.forEach { episode ->
                val current = episodeDao.get(episode.trackId)
                if (current == null || current.updatedAt < episode.updatedAt) episodeDao.upsert(episode.toEntity())
            }
        }

        // Version 1 and 2 files hold sign-ins inside `entries`; version 3 keeps them apart.
        val values = (document.entries + document.accounts).toPreferenceValues()
        val replaceSettings = BackupSection.SETTINGS in sections
        val replaceAccounts = BackupSection.ACCOUNTS in sections
        if (replaceSettings || replaceAccounts) {
            secureStorage.importAll(values) { key ->
                if (secureStorage.isAccountKey(key)) replaceAccounts else replaceSettings
            }
        }

        return BackupContents(
            settings = values.size,
            plays = document.history.size,
            recaps = document.recaps.size,
            tracks = tracksRestored,
            playlists = document.playlists.size
        )
    }

    /** New tracks go in whole; ones already here only gain the backup's likes and counts. */
    private suspend fun restoreTracks(tracks: List<BackupTrack>): Int {
        val entities = tracks.mapNotNull { it.toEntity() }
        entities.chunked(QUERY_BATCH).forEach { trackDao.insertNewTracks(it) }
        entities.forEach {
            trackDao.mergeRestoredState(it.id, it.isLiked, it.isLibrary, it.playCount, it.lastPlayedTimestamp)
        }
        entities.filter { it.isEpisode }.map { it.id }.chunked(QUERY_BATCH).forEach { trackDao.markAsEpisodes(it) }
        return entities.size
    }

    /**
     * Adds restored plays without ever removing one. Restoring last month's backup onto a device
     * that has been playing since must not delete what it played in between, and plays already
     * present are skipped so restoring the same file twice does not double a year's listening.
     */
    private suspend fun restoreHistory(plays: List<BackupPlay>) {
        if (plays.isEmpty()) return
        val existing = historyDao.allPlays().mapTo(mutableSetOf()) { it.trackId to it.playedAt }
        plays.filterNot { (it.trackId to it.playedAt) in existing }
            .chunked(QUERY_BATCH)
            .forEach { batch -> historyDao.insertPlays(batch.map { it.toEntity() }) }
    }

    /** The newer edit of a playlist wins; a playlist only this device has is left alone. */
    private suspend fun restorePlaylists(playlists: List<BackupPlaylist>) {
        val current = playlistDao.getAllPlaylists().associateBy { it.id }
        playlists.forEach { playlist ->
            val here = current[playlist.id]
            if (here == null || here.updatedAt < playlist.updatedAt) playlistDao.insertPlaylist(playlist.toEntity())
        }
    }

    private companion object {
        /** A restore can carry tens of thousands of rows; SQLite caps bound parameters per query. */
        const val QUERY_BATCH = 500
    }
}
