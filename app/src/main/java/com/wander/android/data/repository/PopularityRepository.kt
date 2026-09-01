package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.database.dao.PendingScrobble
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroPlayCount
import com.wander.android.data.sources.agro.AgroPopularityApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Popular on Agro" shelf, and this device's contribution to what it shows.
 *
 * Both halves here because they are one feature seen from two ends, and because the asymmetry
 * between them is the thing worth keeping in one place: reading the totals is free and needs no
 * permission, while contributing to them is off until the user says otherwise. Reporting scrobbles
 * already tells *their own* server what they played; contributing counts makes it part of a total
 * other accounts on that server can see, and that is a disclosure to other people rather than to
 * the server.
 */
@Singleton
class PopularityRepository @Inject constructor(
    private val popularityApi: AgroPopularityApi,
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage
) {

    /**
     * The fleet's popular recordings, as tracks this device can actually play.
     *
     * Resolved against the local library and **nothing else**. The server sends titles and artists,
     * not ids — it has no rows to point at — so every entry has to be matched to something here
     * before it can be put in front of anyone. Matching is the same normalised key the server
     * counted on, computed by the same rules on both sides, so this is a lookup rather than a
     * search.
     *
     * Entries with no local match are dropped rather than resolved by searching the backends. A
     * search answers *something* for almost any query, and a shelf that quietly offered a cover or
     * a karaoke version under the label "what your household is playing" would be worse than a
     * shorter shelf. It does mean the shelf only ever shows music the user already has, which is
     * the honest limit of building it from an id-less total.
     */
    suspend fun popularTracks(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        if (secureStorage.agroServerUrl.isBlank()) return@withContext emptyList()

        val entries = popularityApi.popularTracks(limit = limit * OVERFETCH).getOrElse {
            Log.w(TAG, "popular tracks unavailable: ${it.message}")
            return@withContext emptyList()
        }
        if (entries.isEmpty()) return@withContext emptyList()

        val localByKey = trackDao.getAllTracksOnce()
            .map(TrackEntity::toUnifiedTrack)
            .groupBy { TrackDeduplicator.recordingKey(it) }

        entries.mapNotNull { entry ->
            val key = TrackDeduplicator.recordingKey(
                UnifiedTrack(
                    id = "",
                    source = com.wander.android.data.model.SourceType.LOCAL,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    durationMs = 0L
                )
            )
            // Best-ranked copy, the same preference the rest of the app applies to a recording
            // held more than once.
            localByKey[key]?.minByOrNull { it.source.priority }
        }.distinctBy { it.id }.take(limit)
    }

    /**
     * Adds a batch of plays to the shared totals, if the user has opted in.
     *
     * Called with plays that have *already* been reported as scrobbles and marked, so a failure
     * here cannot cost the user their own history — only a few counts off a shelf. That ordering is
     * deliberate: the counts are the losable half.
     *
     * Aggregated before sending. One request carrying "this recording, six times" rather than six
     * plays with six moments in it is both smaller and less revealing, and day-level totals are all
     * the server keeps anyway.
     */
    suspend fun contribute(plays: List<PendingScrobble>) {
        if (!secureStorage.agroPopularityContribution || plays.isEmpty()) return

        val counts = plays
            .groupBy { TrackDeduplicator.normalizeArtist(it.artist) to TrackDeduplicator.normalizeTitle(it.title) }
            .mapNotNull { (_, group) ->
                val first = group.first()
                if (first.title.isBlank() || first.artist.isBlank()) return@mapNotNull null
                AgroPlayCount(
                    title = first.title,
                    artist = first.artist,
                    album = first.album,
                    count = group.size
                )
            }
        if (counts.isEmpty()) return

        popularityApi.submitPlayCounts(counts).onFailure { error ->
            // Dropped, not retried. See AgroPopularityApi.submitPlayCounts: without a submitter
            // identity a retry is indistinguishable from more listening, so re-sending would
            // inflate the very total it is trying to report honestly.
            Log.w(TAG, "play counts not contributed: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "Popularity"

        /**
         * Asked for more than will be shown, because entries the library does not hold are
         * dropped. A household whose top ten includes three things this phone has never had should
         * still fill its shelf.
         */
        const val OVERFETCH = 3
    }
}
