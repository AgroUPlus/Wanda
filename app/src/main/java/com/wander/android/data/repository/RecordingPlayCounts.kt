package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play counts that belong to the recording rather than to the copy you happened to play.
 *
 * The last half of the recording model. A song held on Navidrome and on YouTube Music counted
 * twice, separately: twelve plays here and eight there is a song played twenty times that ranks
 * below one played fifteen, and "On repeat" showed the same song twice with the wrong number under
 * each.
 *
 * **Nothing is written and nothing is deleted.** The original plan was a migration that folded the
 * rows together — summing counts onto one survivor and deleting the rest — and that is what made
 * this the half with no way back: a wrong merge would silently destroy a recording the user owns,
 * and a rendition deleted is also a source `RenditionFinder` can no longer offer. Totalling on the
 * way out costs one grouping pass over the played rows and has no failure mode worse than a list
 * being briefly wrong. There is nothing to undo because there is nothing to un-write.
 *
 * It also means the totals honour [SplitSet]. A migration would have had to decide once, at
 * migration time, and be wrong forever afterwards for anything the user pinned apart later.
 */
@Singleton
class RecordingPlayCounts @Inject constructor(
    private val trackDao: TrackDao,
    private val splitRepository: RecordingSplitRepository,
    private val linkRepository: RecordingLinkRepository
) {

    /**
     * The most-played recordings, each counting every copy of itself once.
     *
     * The row returned for a group is its best-ranked rendition — the same one the rest of the app
     * would show — carrying the recording's **total** in `playCount` and the most recent play of
     * any copy in `lastPlayedTimestamp`. Callers display it exactly as they displayed a row.
     */
    suspend fun topRecordings(limit: Int): List<UnifiedTrack> = grouped()
        .sortedWith(
            compareByDescending<UnifiedTrack> { it.playCount }.thenBy { it.title }
        )
        .take(limit)

    /**
     * Well-loved recordings not returned to lately — the "forgotten favourites" mix.
     *
     * Totalled first and filtered second, which is the whole point: a recording played five times
     * across two copies passes a threshold that neither copy passes alone.
     */
    suspend fun forgottenFavourites(thresholdTimestamp: Long, minPlays: Int, limit: Int):
        List<UnifiedTrack> = grouped()
        .filter { it.playCount > minPlays }
        .filter { it.lastPlayedTimestamp == null || it.lastPlayedTimestamp!! < thresholdTimestamp }
        .sortedByDescending { it.playCount }
        .take(limit)

    /** One entry per recording, with its copies' plays totalled onto the best-ranked rendition. */
    private suspend fun grouped(): List<UnifiedTrack> {
        val tracks = withContext(Dispatchers.IO) {
            trackDao.getPlayedTracksOnce().map(TrackEntity::toUnifiedTrack)
        }
        val splits = splitRepository.splits()
        val links = linkRepository.links()
        return withContext(Dispatchers.Default) {
            TrackDeduplicator.groupRecordings(tracks, splits, links).mapNotNull { group ->
                // `groupRecordings` sorts a group by source priority, so the first is the one the
                // rest of the app would have shown for this recording.
                group.firstOrNull()?.copy(
                    playCount = group.sumOf { it.playCount },
                    lastPlayedTimestamp = group.mapNotNull { it.lastPlayedTimestamp }.maxOrNull()
                )
            }
        }
    }
}
