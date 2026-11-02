package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.database.dao.LikeSyncDao
import com.wander.android.core.database.dao.MAX_BOUND_IDS
import com.wander.android.core.database.entity.RemoteLikeEntity
import com.wander.android.core.network.loggableMessage
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource

/**
 * Brings each backend's liked songs into Room: Navidrome's starred songs, YouTube Music's liked
 * music, Deezer's favourites. Nothing read them before, so a song liked on the server or in
 * another app never showed as liked here.
 *
 * Two things a wish made in Wanda wins over: a like or unlike still in the [LikeOutbox] is left as
 * the user set it, and an unlike is mirrored only for a track the backend itself confirmed as
 * liked before (see `remote_likes`) — never for a like that only ever existed in Wanda. Unlikes are
 * read only from a list read to its end: a walk cut short says nothing about what it did not reach.
 */
internal class RemoteLikesImport(
    private val dao: LikeSyncDao,
    private val activeSources: () -> List<IMusicSource>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit
) {
    /**
     * Imports every connected source's likes. A source that cannot be read keeps the likes Room
     * already has for it, and the next refresh tries again; that is logged, not shown, since the
     * Liked list on screen is still right as far as anything here knows.
     */
    suspend fun importAll() {
        for (source in activeSources()) {
            if (!source.capabilities.likes) continue
            importFrom(source).onFailure {
                Log.w(TAG, "${source.displayName} likes not imported: ${it.loggableMessage()}")
            }
        }
    }

    private suspend fun importFrom(source: IMusicSource): Result<Unit> {
        val walk = readLikes(source).getOrElse { return Result.failure(it) }
        val liked = walk.tracks.filter { it.source == source.sourceType }
        val likedIds = liked.mapTo(LinkedHashSet()) { it.id }
        val pending = dao.pendingTrackIds().toHashSet()

        persist(liked, true)
        (likedIds - pending).chunked(MAX_BOUND_IDS).forEach { dao.setTracksLiked(it, liked = true) }
        dao.recordRemoteLikes(likedIds.map { RemoteLikeEntity(it, source.sourceType) })

        if (walk.complete) {
            val dropped = dao.remoteLikedIds(source.sourceType).filter { it !in likedIds }
            dropped.chunked(MAX_BOUND_IDS).forEach { dao.forgetRemoteLikes(it) }
            dropped.filter { it !in pending }
                .chunked(MAX_BOUND_IDS)
                .forEach { dao.setTracksLiked(it, liked = false) }
        }
        return Result.success(Unit)
    }

    /** Pages through [source]'s likes until it runs dry, repeats itself, or hits the cap. */
    private suspend fun readLikes(source: IMusicSource): Result<LikeWalk> {
        val collected = ArrayList<UnifiedTrack>()
        val seen = HashSet<String>()
        var offset = 0
        while (collected.size < MAX_LIKES) {
            val page = source.getLikedTracks(PAGE_SIZE, offset).getOrElse { return Result.failure(it) }
            // Only an empty page ends the list: a source may return a short page mid-list when it
            // drops entries it cannot show, and taking that for the end would read as unlikes.
            if (page.isEmpty()) return Result.success(LikeWalk(collected, complete = true))
            val fresh = page.filter { seen.add(it.id) }
            collected += fresh
            // A full page of nothing new: the source ignored the offset, and the rest is unknown.
            if (fresh.isEmpty()) return Result.success(LikeWalk(collected, complete = false))
            offset += page.size
        }
        return Result.success(LikeWalk(collected, complete = false))
    }

    private class LikeWalk(val tracks: List<UnifiedTrack>, val complete: Boolean)

    private companion object {
        const val TAG = "RemoteLikesImport"
        const val PAGE_SIZE = 500
        const val MAX_LIKES = 10_000
    }
}
