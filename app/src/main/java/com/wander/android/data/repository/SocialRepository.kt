package com.wander.android.data.repository

import com.wander.android.data.sources.agro.AgroDeviceKey
import com.wander.android.core.database.dao.FriendDao
import com.wander.android.core.database.entity.FriendEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroFeedApi
import com.wander.android.data.sources.agro.AgroFeedItem
import com.wander.android.data.sources.agro.AgroFriend
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroFriendsApi
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.AgroProfileApi
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.AgroStatsApi
import com.wander.android.data.sources.agro.AgroTasteMatch
import com.wander.android.data.sources.agro.AgroVisibility
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.data.sources.agro.StatsPeriod
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * The friend graph and the presence feed, with a deliberate split down the middle.
 *
 * **The graph is cached in Room** and read back as a Flow, like every other list in this app: it
 * changes rarely, and a Friends screen that is blank until the network answers is worse than one
 * that shows yesterday's list while it refreshes.
 *
 * **Presence is held in memory and nowhere else.** It is someone else's listening; the switch that
 * permits it can be withdrawn at any moment, and a row on this device's disk would outlive that
 * withdrawal. It is also stale within minutes, which makes persisting it pointless as well as
 * wrong.
 */
@Singleton
internal class SocialRepository @Inject constructor(
    private val friendsApi: AgroFriendsApi,
    private val profileApi: AgroProfileApi,
    private val friendDao: FriendDao,
    private val statsApi: AgroStatsApi,
    private val feedApi: AgroFeedApi,
    private val secureStorage: SecureStorage,
    private val friendKeys: FriendKeyDirectory
) {
    val friends: Flow<List<AgroProfile>> =
        friendDao.observeFriends().map { rows -> rows.map { it.toProfile() } }

    val requests: Flow<List<AgroProfile>> =
        friendDao.observeRequests().map { rows -> rows.map { it.toProfile() } }

    /**
     * What friends have been into lately.
     *
     * The server has answered `friendActivity` for as long as the feed has existed, and only the
     * Circle screen ever asked — so the Friends tab, which is the screen people actually open,
     * showed a static roster and nothing else. Held in memory rather than cached in Room: it is a
     * derived view of everyone's scrobbles that goes stale by the hour, and a stale feed shown
     * offline would be worse than none.
     */
    private val _feed = MutableStateFlow<List<AgroFeedItem>>(emptyList())
    val feed: StateFlow<List<AgroFeedItem>> = _feed.asStateFlow()

    /** True while a longer page is in flight, so the list can show it is still coming. */
    private val _feedLoadingMore = MutableStateFlow(false)
    val feedLoadingMore: StateFlow<Boolean> = _feedLoadingMore.asStateFlow()

    /**
     * True once the server has answered with fewer items than were asked for.
     *
     * That is the only end-of-feed signal available: `friendActivity` takes a window and a limit
     * and has no cursor, so a short page is what "there is no more" looks like.
     */
    private val _feedExhausted = MutableStateFlow(false)
    val feedExhausted: StateFlow<Boolean> = _feedExhausted.asStateFlow()

    /** How many items the feed is currently asking for. Grows as the list is scrolled. */
    private var feedLimit = FEED_PAGE

    /**
     * Asks for a longer feed.
     *
     * The window grows rather than an offset advancing, because the server's `friendActivity`
     * takes only `days` and `limit` — there is no cursor to continue from. Re-reading a longer
     * prefix is more traffic than a cursor would be, and it is what the existing schema supports;
     * a real cursor belongs on the Agro side and is not something a client can fake safely, since
     * an offset into a feed that is recomputed per request would silently skip items.
     *
     * Once the window covers everything the server has, this stops asking.
     */
    suspend fun loadMoreFeed() {
        if (!secureStorage.agroConfigured.value) return
        if (_feedExhausted.value || _feedLoadingMore.value) return
        _feedLoadingMore.value = true
        val want = feedLimit + FEED_PAGE
        feedApi.friendActivity(days = FEED_DAYS, limit = want).onSuccess { items ->
            feedLimit = want
            _feed.value = items
            _feedExhausted.value = items.size < want
        }
        _feedLoadingMore.value = false
    }

    private val _nowPlaying = MutableStateFlow<List<AgroFriendNowPlaying>>(emptyList())
    val nowPlaying: StateFlow<List<AgroFriendNowPlaying>> = _nowPlaying.asStateFlow()

    val isPaired: StateFlow<Boolean> get() = secureStorage.agroConfigured

    /**
     * Re-reads the graph and the feed together.
     *
     * Called on foreground and on a `FRIEND_PRESENCE` frame. There is no polling loop: the socket
     * is the live path, and a timer would keep the radio awake to learn something the server is
     * already pushing.
     */
    suspend fun refresh(): Result<Unit> {
        if (!secureStorage.agroConfigured.value) {
            _nowPlaying.value = emptyList()
            return Result.success(Unit)
        }

        val friendResult = friendsApi.friends()
        val requestResult = friendsApi.friendRequests()
        // Failure leaves whatever was there. An empty feed and a feed that could not be fetched
        // look identical on screen, and only one of them means "nobody has done anything".
        feedApi.friendActivity(days = FEED_DAYS, limit = feedLimit).onSuccess { items ->
            _feed.value = items
            // A refresh re-reads the window the user has already scrolled open, so scrolling back
            // up and pulling to refresh does not collapse the feed to its first page.
            _feedExhausted.value = items.size < feedLimit
        }

        // The cache is only replaced when *both* answered. A partial write would delete every
        // pending request on a refresh whose second call happened to fail.
        val friends = friendResult.getOrNull()
        val requests = requestResult.getOrNull()
        if (friends != null && requests != null) {
            val now = System.currentTimeMillis()
            friendDao.replaceAll(
                friends.map { it.profile.toEntity(FriendState.ACCEPTED, now) } +
                    requests.map { it.toEntity(FriendState.PENDING, now) }
            )
            _nowPlaying.value = friends.mapNotNull(AgroFriend::nowPlaying)
            return Result.success(Unit)
        }
        return Result.failure(
            friendResult.exceptionOrNull() ?: requestResult.exceptionOrNull()
                ?: IllegalStateException("Could not read the friend list")
        )
    }

    /** Just the feed, for a presence frame that did not change the graph. */
    suspend fun refreshPresence() {
        if (!secureStorage.agroConfigured.value) return
        friendsApi.friendsNowPlaying().onSuccess { _nowPlaying.value = it }
    }

    suspend fun search(query: String): Result<List<AgroProfile>> = friendsApi.searchUsers(query)

    suspend fun profile(username: String): Result<AgroProfile?> = profileApi.profile(username)

    /**
     * Every identity key an account has published, one per device.
     *
     * Not cached, deliberately — see `AgroProfileApi.deviceKeys`. A key list is the one thing here
     * where being slightly out of date produces a message nobody can ever read.
     */
    suspend fun deviceKeys(username: String): Result<List<AgroDeviceKey>> =
        profileApi.deviceKeys(username)

    /**
     * Applies one friend's new track, in place, with no network call.
     *
     * Instant and uncancellable, which the re-query it replaces was neither: two HTTP calls per
     * track change, racing whatever the friend played next.
     */
    fun applyPresence(update: AgroFriendNowPlaying) {
        _nowPlaying.value = _nowPlaying.value
            .filterNot { it.username.equals(update.username, ignoreCase = true) }
            .plus(update)
            // A friend who paused is still present; one playing nothing at all is not.
            .filter { it.trackTitle.isNotBlank() }
    }

    suspend fun tasteMatch(username: String): Result<AgroTasteMatch> =
        profileApi.tasteMatch(username)

    /**
     * A friend's listening statistics, when they allow it.
     *
     * Fails rather than returning empty when the switch is off, for the same reason `tasteMatch`
     * does: "they keep this private" and "they have listened to nothing" are different answers and
     * the screen says different things about them.
     */
    suspend fun friendStats(username: String): Result<AgroStats> =
        statsApi.listeningStats(StatsPeriod.ALL, username = username)

    suspend fun updateProfile(displayName: String?, bio: String?, avatarUrl: String?) =
        profileApi.updateProfile(displayName, bio, avatarUrl)

    suspend fun setVisibility(visibility: AgroVisibility) = profileApi.setVisibility(visibility)

    /** Every graph write refreshes, because all of them change what the lists should show. */
    suspend fun sendRequest(username: String): Result<Boolean> =
        friendsApi.sendFriendRequest(username).alsoRefresh()

    suspend fun accept(username: String): Result<Boolean> =
        friendsApi.acceptFriendRequest(username).alsoRefresh()

    suspend fun remove(username: String): Result<Boolean> =
        friendsApi.removeFriend(username).alsoRefresh()

    suspend fun block(username: String): Result<Boolean> =
        friendsApi.blockUser(username).alsoRefresh()

    /** A short-lived code for adding this account as a friend in person. */
    suspend fun createFriendCode() = friendsApi.createFriendCode()

    suspend fun revokeFriendCode() = friendsApi.revokeFriendCode()

    /** Spends somebody's code. Answers with their username, or null for every kind of failure. */
    suspend fun redeemFriendCode(code: String): Result<String?> =
        friendsApi.redeemFriendCode(code).also { if (it.getOrNull() != null) refresh() }

    private suspend fun Result<Boolean>.alsoRefresh(): Result<Boolean> =
        also {
            if (it.getOrDefault(false)) {
                // The cached key lists describe a graph that has just changed. Sealing the next
                // track to them would either miss a new friend or keep sealing to one who is gone.
                friendKeys.invalidate()
                refresh()
            }
        }

    /** Cleared on unpair: another account's friends are not this one's to keep. */
    suspend fun clear() {
        friendDao.clear()
        friendKeys.invalidate()
        _nowPlaying.value = emptyList()
    }

    private companion object {
        /**
         * One page of activity. Large enough that the first screen is full on any phone, small
         * enough that the first refresh of the tab is not a long request.
         */
        const val FEED_PAGE = 30

        /** The window the feed covers. Unchanged from what the tab has always asked for. */
        const val FEED_DAYS = 14
    }
}

private fun AgroProfile.toEntity(state: FriendState, now: Long) = FriendEntity(
    username = username,
    displayName = displayName,
    bio = bio,
    avatarUrl = avatarUrl,
    state = if (state == FriendState.ACCEPTED) "accepted" else "pending",
    outgoing = outgoing,
    showNowPlaying = showNowPlaying,
    showStats = showStats,
    syncedAt = now
)

private fun FriendEntity.toProfile() = AgroProfile(
    username = username,
    displayName = displayName,
    bio = bio,
    avatarUrl = avatarUrl,
    // Not cached: it is only ever "when this row was written", which is not something the UI shows.
    createdAt = "",
    friendState = if (state == "accepted") FriendState.ACCEPTED else FriendState.PENDING,
    outgoing = outgoing,
    showNowPlaying = showNowPlaying,
    showStats = showStats,
    // Whether a friend is listed in the public directory is not this device's business, and the
    // cache would only ever have a stale answer.
    discoverable = false
)
