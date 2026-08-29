package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SmartMix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-press mixes for the Home screen, derived from listening history.
 *
 * Every one is a Room read of the user's *own* listening — Home is about their music, not about
 * a catalogue to go browsing. A mix with no tracks is not shown.
 */
@Singleton
class SmartMixRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val recordingPlayCounts: RecordingPlayCounts
) {
    suspend fun getSmartMixes(): List<SmartMix> = withContext(Dispatchers.IO) {
        listOfNotNull(
            mix(
                id = "personal_radio",
                // Named to match the player's Radio chip, so one feature has one name.
                title = "Your Radio",
                subtitle = "Built from what you play most",
                iconName = "radio",
                gradient = listOf(0xFF6366F1, 0xFFEC4899),
                // Per recording: a song split across two backends was under-counted and could
                // miss its own radio, while both of its copies could also turn up in it.
                tracks = recordingPlayCounts.topRecordings(40).shuffled().take(20)
            ),
            mix(
                id = "forgotten_favorites",
                title = "Forgotten Favourites",
                subtitle = "Loved once, not heard lately",
                iconName = "history",
                gradient = listOf(0xFFF59E0B, 0xFFEF4444),
                // Totalled before the threshold is applied: a recording played five times across
                // two copies belongs here even though neither copy passes the bar alone.
                tracks = recordingPlayCounts.forgottenFavourites(
                    thresholdTimestamp = forgottenThreshold(),
                    minPlays = FORGOTTEN_MIN_PLAYS,
                    limit = 30
                )
            ),
            mix(
                id = "fresh_discoveries",
                title = "Never Played",
                subtitle = "In your library, still unheard",
                iconName = "auto_awesome",
                gradient = listOf(0xFF10B981, 0xFF3B82F6),
                tracks = trackDao.getNeverPlayedTracks(30).toTracks()
            )
        )
    }

    private fun mix(
        id: String,
        title: String,
        subtitle: String,
        iconName: String,
        gradient: List<Long>,
        tracks: List<com.wander.android.data.model.UnifiedTrack>
    ): SmartMix? = tracks.takeIf { it.isNotEmpty() }?.let {
        SmartMix(
            id = "mix:$id",
            title = title,
            subtitle = subtitle,
            iconName = iconName,
            gradientColors = gradient,
            seedType = id,
            tracks = it
        )
    }

    private fun List<TrackEntity>.toTracks() = map(TrackEntity::toUnifiedTrack)

    private companion object {
        val FORGOTTEN_AFTER_DAYS = TimeUnit.DAYS.toMillis(30)

        /** The bar the old SQL used, kept so the mix means the same thing it always did. */
        const val FORGOTTEN_MIN_PLAYS = 3

        fun forgottenThreshold() = System.currentTimeMillis() - FORGOTTEN_AFTER_DAYS
    }
}
