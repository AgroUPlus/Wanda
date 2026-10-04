package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.FriendDao
import com.wander.android.data.sources.agro.AgroSharedListing
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The playlists friends have shared with this account, followed or not, with what it may do in
 * each. Read from Agro when asked: it is a view of other people's choices, which change without
 * this device hearing of it, so a stored copy would mostly be a way to be out of date.
 */
@Singleton
class SharedWithMeRepository @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val friendDao: FriendDao,
    private val shared: SharedPlaylistRepository
) {
    val isAvailable: Boolean get() = api.isAvailable

    /**
     * Friends' playlists this account can open, every blend it is in, and anything it already
     * follows. A stranger's public playlist is open to everyone on the server and shared with
     * nobody in particular, so it is left out unless followed.
     */
    suspend fun list(): Result<List<AgroSharedListing>> {
        if (!api.isAvailable) return Result.success(emptyList())
        val friends = friendDao.friendUsernames().mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        return api.openToMe().map { all ->
            all.filter { it.isBlend || it.isFollowing || it.owner.lowercase(Locale.ROOT) in friends }
                .sortedBy { it.title.lowercase(Locale.ROOT) }
        }
    }

    /** The id to open [listing] by, following it first when this device holds no copy yet. */
    suspend fun open(listing: AgroSharedListing): Result<String> {
        if (shared.followedPlaylist(listing.id) != null) return Result.success(SharedPlaylistMirror.routeId(listing.id))
        return shared.follow(listing.id)
    }
}
