package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** One recording, and the rows that would be folded into it. */
data class MergeGroup(
    val title: String,
    val artist: String,
    val renditions: List<UnifiedTrack>,
    /** How many of these rows are liked. More than one means a like is currently split. */
    val likedRenditions: Int,
    /** What the recording's play count would become. */
    val combinedPlays: Int,
    /** Largest gap between any two lengths in the group, in milliseconds. */
    val durationSpreadMs: Long,
    /** Distinct album names across the group. More than one is worth a human look. */
    val albums: List<String>
) {
    /**
     * Whether this merge deserves checking by eye before it is written.
     *
     * Not "wrong" — suspicious. A group whose lengths sit near the tolerance edge, or whose rows
     * disagree about which record they came from, is where a live take or an alternate mix would
     * hide if the matching were going to make a mistake.
     */
    val needsReview: Boolean
        get() = durationSpreadMs > REVIEW_SPREAD_MS || albums.size > 1

    private companion object {
        /** Two thirds of the matcher's tolerance: close enough to pass, far enough to look at. */
        const val REVIEW_SPREAD_MS = 2_000L
    }
}

/** What migrating to recordings would do, without doing any of it. */
data class MergeReport(
    val trackRows: Int,
    val recordings: Int,
    val groups: List<MergeGroup>,
    /** Likes currently split across rows that would become one. */
    val splitLikes: Int
) {
    val merged: Int get() = trackRows - recordings
    val reviewable: List<MergeGroup> get() = groups.filter { it.needsReview }
}

/**
 * A dry run of the recording migration.
 *
 * The migration itself walks every track, folds renditions of one performance together, and sums
 * their play counts onto the recording. That is a one-way write over a year of listening history:
 * if the matching is too loose on a real library — as opposed to the fixtures it was tested with —
 * a live take absorbs its studio original and there is no undo.
 *
 * So it is worth being able to *look* first. This computes exactly what the migration would do,
 * against the user's own library, and writes nothing.
 */
@Singleton
class RecordingMergePreview @Inject constructor(
    private val trackDao: TrackDao
) {

    suspend fun preview(): MergeReport = withContext(Dispatchers.Default) {
        val tracks = withContext(Dispatchers.IO) {
            trackDao.getAllTracksOnce().map(TrackEntity::toUnifiedTrack)
        }
        val groups = TrackDeduplicator.groupRecordings(tracks)

        val merges = groups
            .filter { it.size > 1 }
            .map { group ->
                val durations = group.map { it.durationMs }
                MergeGroup(
                    title = group.first().title,
                    artist = group.first().artist,
                    renditions = group,
                    likedRenditions = group.count { it.isLiked },
                    combinedPlays = group.sumOf { it.playCount },
                    durationSpreadMs = abs((durations.maxOrNull() ?: 0L) - (durations.minOrNull() ?: 0L)),
                    albums = group.mapNotNull { it.album?.takeIf(String::isNotBlank) }.distinct()
                )
            }
            // Worst first: the groups most worth a human's attention, then the biggest merges.
            .sortedWith(compareByDescending<MergeGroup> { it.needsReview }.thenByDescending { it.renditions.size })

        MergeReport(
            trackRows = tracks.size,
            recordings = groups.size,
            groups = merges,
            splitLikes = merges.count { it.likedRenditions in 1 until it.renditions.size }
        )
    }
}
