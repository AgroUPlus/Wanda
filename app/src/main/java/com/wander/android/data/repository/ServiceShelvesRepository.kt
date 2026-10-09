package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

/** Why a shelf drawn from an online service has nothing to show. */
enum class ServiceProblem {
    AGRO_NOT_PAIRED,
    AGRO_UNREACHABLE,
    AGRO_NO_DATA,
    AGRO_NOTHING_IN_LIBRARY,
    YOUTUBE_NOT_CONNECTED,
    YOUTUBE_UNREACHABLE,
    YOUTUBE_NO_SHELVES
}

/** A service shelf's songs, or the reason there are none. */
class ServiceShelfResult(val tracks: List<UnifiedTrack>, val problem: ServiceProblem? = null)

/**
 * Songs for the shelves that depend on a service: Popular on Agro, and YouTube Music's picks. When
 * a service has nothing to give, the result says why, so the customizer can tell the user what to
 * fix instead of showing an unexplained empty shelf.
 */
@Singleton
class ServiceShelvesRepository @Inject constructor(
    private val popularity: PopularityRepository,
    private val recommendations: RecommendationRepository,
    private val music: MusicRepository
) {

    suspend fun agroPopular(limit: Int): ServiceShelfResult = when (val outcome = popularity.popularOutcome(limit)) {
        is PopularOutcome.Tracks -> ServiceShelfResult(outcome.tracks)
        PopularOutcome.NotPaired -> ServiceShelfResult(emptyList(), ServiceProblem.AGRO_NOT_PAIRED)
        PopularOutcome.Unreachable -> ServiceShelfResult(emptyList(), ServiceProblem.AGRO_UNREACHABLE)
        PopularOutcome.NoData -> ServiceShelfResult(emptyList(), ServiceProblem.AGRO_NO_DATA)
        PopularOutcome.NothingInLibrary -> ServiceShelfResult(emptyList(), ServiceProblem.AGRO_NOTHING_IN_LIBRARY)
    }

    /** A mix of what YouTube Music's own front page offers. */
    suspend fun youtubePicks(limit: Int): ServiceShelfResult {
        val youtube = music.activeSources().firstOrNull { it.sourceType == SourceType.YTMUSIC }
            ?: return ServiceShelfResult(emptyList(), ServiceProblem.YOUTUBE_NOT_CONNECTED)

        val shelves = recommendations.getShelves().filter { it.id.startsWith(YOUTUBE_SHELF_PREFIX) }
        if (shelves.isNotEmpty()) {
            return ServiceShelfResult(shelves.flatMap { it.tracks }.distinctBy { it.id }.shuffled().take(limit))
        }
        // The feed came back empty, which hides whether the connection failed or there was simply
        // nothing; ask once more to find out.
        return youtube.getRecommendations().fold(
            onSuccess = { ServiceShelfResult(emptyList(), ServiceProblem.YOUTUBE_NO_SHELVES) },
            onFailure = { ServiceShelfResult(emptyList(), ServiceProblem.YOUTUBE_UNREACHABLE) }
        )
    }

    private companion object {
        /** Ids YouTube Music gives its shelves; see `shelfId` in the home parser. */
        const val YOUTUBE_SHELF_PREFIX = "ytm_"
    }
}
