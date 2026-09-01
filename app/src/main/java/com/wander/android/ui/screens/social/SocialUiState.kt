package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.data.sources.agro.AgroFeedItem
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile

/**
 * The Friends tab.
 *
 * [isPaired] gates the whole screen: friends are an Agro feature and there is nothing honest to
 * show without a server. [error] never empties the lists — a failed refresh has not discovered that
 * you have no friends, so the cached graph stays on screen with a note above it.
 */
@Immutable
internal data class SocialUiState(
    val isPaired: Boolean = false,
    val friends: List<AgroProfile> = emptyList(),
    val incoming: List<AgroProfile> = emptyList(),
    val outgoing: List<AgroProfile> = emptyList(),
    val nowPlaying: List<AgroFriendNowPlaying> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val session: ListenAlongSession? = null,
    /** What friends have been into lately. Empty while offline, and that is not an error. */
    val feed: List<AgroFeedItem> = emptyList(),
    /** True while a longer page of activity is on its way, which is what draws the skeletons. */
    val feedLoadingMore: Boolean = false,
    /** True once the server has nothing further to add, so the list stops asking. */
    val feedExhausted: Boolean = false,
    /** This account's own username, for the avatar that opens your own profile. */
    val myUsername: String = "",
    /** This account's own picture, when it has one. */
    val myAvatarUrl: String? = null,
    /**
     * True until the cached graph has been read once.
     *
     * Distinct from [isRefreshing], which is true on every refresh over content already on
     * screen — this one is what decides between placeholders and a blank tab.
     */
    val loading: Boolean = true
) {
    /** True only when there is genuinely nothing yet — not merely while a refresh is in flight. */
    val isEmpty: Boolean
        get() = friends.isEmpty() && incoming.isEmpty() && outgoing.isEmpty()

    /** What a given friend is playing, when they let it be seen. */
    fun playing(username: String): AgroFriendNowPlaying? =
        nowPlaying.firstOrNull { it.username.equals(username, ignoreCase = true) }
}

/** The user-search sheet, which has a small lifecycle of its own. */
@Immutable
internal data class UserSearchState(
    val query: String = "",
    val results: List<AgroProfile> = emptyList(),
    val isSearching: Boolean = false,
    /** Usernames just requested, so a row can say so before the next refresh lands. */
    val requested: Set<String> = emptySet(),
    val error: String? = null,
    /** True while the QR panel is showing. Never persisted — the code dies with the sheet. */
    val showingCode: Boolean = false,
    /** Null while one is being minted. */
    val friendCode: String? = null
)
