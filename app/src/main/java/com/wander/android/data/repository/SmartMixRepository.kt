package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SmartMix
import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-press mixes for the Home screen, derived from listening history.
 *
 * A mix with no tracks is not shown. The previous version always emitted an "Internet Archive
 * Gems" tile with an empty track list, so tapping it did nothing.
 */
@Singleton
class SmartMixRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val sources: Set<@JvmSuppressWildcards IMusicSource>
) {
    suspend fun getSmartMixes(): List<SmartMix> = coroutineScope {
        val archive = async { archiveSpotlight() }

        val mixes = withContext(Dispatchers.IO) {
            listOfNotNull(
                mix(
                    id = "personal_radio",
                    // Named to match the player's Radio chip, so one feature has one name.
                    title = "Your Radio",
                    subtitle = "Built from what you play most",
                    iconName = "radio",
                    gradient = listOf(0xFF6366F1, 0xFFEC4899),
                    tracks = trackDao.getTopPlayedTracks(40).toTracks().shuffled().take(20)
                ),
                mix(
                    id = "forgotten_favorites",
                    title = "Forgotten Favourites",
                    subtitle = "Loved once, not heard lately",
                    iconName = "history",
                    gradient = listOf(0xFFF59E0B, 0xFFEF4444),
                    tracks = trackDao.getForgottenFavorites(forgottenThreshold()).toTracks()
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

        mixes + listOfNotNull(archive.await())
    }

    /** Live concerts and rarities, pulled fresh from the Archive rather than from history. */
    private suspend fun archiveSpotlight(): SmartMix? {
        val source = sources.firstOrNull { it.sourceType == SourceType.INTERNET_ARCHIVE }
            ?: return null
        val tracks = source.getRecentTracks(ARCHIVE_TRACKS).getOrDefault(emptyList())
        return mix(
            id = "archive_spotlight",
            title = "Internet Archive Gems",
            subtitle = "Live recordings and rarities",
            iconName = "public",
            gradient = listOf(0xFF8B5CF6, 0xFF06B6D4),
            tracks = tracks
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
        const val ARCHIVE_TRACKS = 25
        val FORGOTTEN_AFTER_DAYS = TimeUnit.DAYS.toMillis(30)

        fun forgottenThreshold() = System.currentTimeMillis() - FORGOTTEN_AFTER_DAYS
    }
}
