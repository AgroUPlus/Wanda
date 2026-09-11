package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Home screen's shelves. Every one of these is a straight Room read — no source ever touches
 * them — so Home renders identically offline and instantly on a cold start.
 *
 * Split out of [MusicRepository], which had grown past the file-size cap and was mixing these
 * one-shot reads with the network-backed browsing they sit next to.
 */
@Singleton
class HomeShelfRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val recordingPlayCounts: RecordingPlayCounts,
    private val recordingRules: RecordingRulesRepository
) {

    /**
     * "Recently played", one entry per recording.
     *
     * Over-fetched and then collapsed: several copies of one song near the top of the list would
     * otherwise fill the carousel with the same track, and taking the limit first would leave
     * fewer than [limit] distinct recordings behind.
     */
    suspend fun getRecentlyPlayed(limit: Int = 20): List<UnifiedTrack> {
        val tracks = withContext(Dispatchers.IO) {
            trackDao.getRecentlyPlayedTracks(limit * OVERFETCH).map(TrackEntity::toUnifiedTrack)
        }
        return recordingRules.current()
            .distinct(tracks)
            .take(limit)
    }

    /**
     * "On repeat", counted per recording rather than per row.
     *
     * A song held on two backends used to appear twice, each with a fraction of its real count,
     * and rank below songs played less. See [RecordingPlayCounts].
     */
    suspend fun getTopTracks(limit: Int = 20): List<UnifiedTrack> =
        recordingPlayCounts.topRecordings(limit)

    suspend fun getLikedTracks(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getLikedTracksList(limit).map(TrackEntity::toUnifiedTrack)
    }

    /** In the library but never listened to. */
    suspend fun getNeverPlayed(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getNeverPlayedTracks(limit).map(TrackEntity::toUnifiedTrack)
    }

    /** Recently added, restricted to one backend, for Home's per-source shelves. */
    suspend fun getRecentBySource(source: SourceType, limit: Int = 12): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            trackDao.getRecentlyAddedInSource(source, limit).map(TrackEntity::toUnifiedTrack)
        }

    /**
     * Home's lead shelf: what gets played most, with every configured backend represented.
     *
     * [getTopTracks] alone is play counts, and play counts lean local. [RecordingPlayCounts] hands
     * back one rendition per recording chosen by source priority — local first, then Navidrome,
     * then YouTube Music — so a library with a few local files could fill the shelf with them and
     * never show the backend holding most of the music. The shelf was "what you play", drawn almost
     * entirely from one source.
     *
     * So the played recordings lead and each source's own recent rows are blended in behind them,
     * round-robin. Still one Room read per source and nothing on the network: this is the shelf the
     * screen opens on, and it has to be there before the first frame, offline included.
     *
     * Duplicates are collapsed by *recording* rather than by id — the same song held on two
     * backends is one pick, not two.
     */
    suspend fun getQuickPicks(
        limit: Int = 20,
        sources: List<SourceType>
    ): List<UnifiedTrack> {
        val top = recordingPlayCounts.topRecordings(limit)
        // One backend configured means there is nothing to balance, and interleaving would only
        // cost the shelf its play-count order.
        if (sources.size <= 1) return top

        val perSource = withContext(Dispatchers.IO) {
            sources.flatMap { source ->
                trackDao.getRecentlyAddedInSource(source, limit).map(TrackEntity::toUnifiedTrack)
            }
        }

        return recordingRules.current()
            .distinct(interleaveBySource(top + perSource))
            .take(limit)
    }

    /**
     * Recently played, one track per album, so the shelf reads as "records you were listening to"
     * rather than repeating six tracks off the same one.
     */
    suspend fun getRecentAlbumStarters(limit: Int = 12): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            trackDao.getRecentlyPlayedTracks(limit * 4)
                .map(TrackEntity::toUnifiedTrack)
                .distinctBy { it.album?.takeIf { name -> name.isNotBlank() } ?: it.id }
                .take(limit)
        }

    private companion object {
        /** How much wider to cast the net before collapsing copies down to recordings. */
        const val OVERFETCH = 3
    }
}
