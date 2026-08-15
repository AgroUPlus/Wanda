package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.RecommendedShelf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The recommendation shelves Home shows above its own library-derived ones.
 *
 * These come from the backends themselves — YouTube Music's front page is YouTube Music's
 * recommender, not an approximation of it built here out of play counts. Nothing is invented: a
 * source that publishes no feed contributes nothing, so with YouTube Music signed out Home is
 * simply the Navidrome and on-device shelves, which is the behaviour the user asked for.
 *
 * Its own class rather than another method on [MusicRepository], which is already at the
 * file-size cap — the same reason [HomeShelfRepository] exists.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val trackDao: TrackDao
) {

    suspend fun getShelves(): List<RecommendedShelf> = coroutineScope {
        val shelves = musicRepository.activeSources()
            .filter { it.capabilities.recommendations }
            .map { source -> async { source.getRecommendations().getOrDefault(emptyList()) } }
            .flatMap { it.await() }

        // Cached, but never marked as library: a suggestion is something the user was shown, not
        // something they own. Room still has to know these rows exist, or liking one — or the
        // play count written when one is played — would update a row that is not there.
        val tracks = shelves.flatMap { it.tracks }.distinctBy { it.id }
        if (tracks.isNotEmpty()) {
            trackDao.upsertTracks(tracks.map { TrackEntity.fromUnifiedTrack(it, isLibrary = false) })
        }

        shelves
    }
}
