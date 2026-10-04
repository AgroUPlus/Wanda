package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.PlaylistRole
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the copies in line with what changed somewhere other than this device: a playlist made
 * or followed in the dashboard or on another device, one unfollowed there, and one this device
 * deleted before deleting a published playlist reached Agro.
 */
@Singleton
class SharedPlaylistDiscovery @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val playlistDao: PlaylistDao,
    private val shared: SharedPlaylistRepository
) {
    /**
     * One look at what this account owns and follows. A playlist that fails to load is left for
     * the next look; either list failing is the error, so being offline is not mistaken for owning
     * or following nothing.
     */
    suspend fun discover(): Result<Unit> = runCatching {
        val owned = api.ownedIds().getOrThrow()
        val followed = api.followedIds().getOrThrow()
        (owned + followed.open).distinct().filter { dao.get(it) == null }.forEach { id -> shared.keepOwn(id) }
        dao.followed()
            .filter { it.agroId !in followed.open && it.agroId !in followed.revoked && isFollowedCopy(it) }
            .forEach { shared.forget(it.agroId) }
        deleteOrphans()
    }

    /**
     * A follow or unfollow made elsewhere, pushed by Agro: keep a copy of a playlist just followed,
     * or let go of one just unfollowed.
     */
    suspend fun followedElsewhere(agroId: String, following: Boolean) {
        val copy = dao.get(agroId)
        when {
            following && copy == null -> shared.keepOwn(agroId)
            !following && copy != null && isFollowedCopy(copy) -> shared.forget(agroId)
        }
    }

    /**
     * Published playlists whose Wanda playlist is gone. Deleting one used to remove it from this
     * device alone, so it stayed on Agro and in every follower's library with no owner able to
     * reach it. Its owner asked for it gone, so it goes from Agro now; one that will not go stays
     * for the next look.
     */
    private suspend fun deleteOrphans() {
        dao.all()
            .filter { copy -> copy.localPlaylistId?.let { playlistDao.getPlaylistById(it) == null } == true }
            .forEach { shared.remove(it.agroId) }
    }

    /**
     * A copy kept only because this account follows it. Not its own, which unfollowing would not
     * remove, and not a blend, which is left rather than unfollowed.
     */
    private fun isFollowedCopy(copy: SharedPlaylistEntity): Boolean =
        copy.localPlaylistId == null && !copy.isBlend && copy.myRole != PlaylistRole.OWNER.name
}
