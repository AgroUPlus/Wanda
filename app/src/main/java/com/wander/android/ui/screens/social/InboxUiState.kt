package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import com.wander.android.data.sources.agro.AgroDrop

/** What the inbox screen draws. */
@Immutable
internal data class InboxUiState(
    /**
     * One entry per person, newest exchange first — the conversation list.
     *
     * Replaces the old received/sent split, which was the wrong seam: two halves of the same
     * exchange, filed apart, so a song and the song sent back in reply lived on different tabs.
     */
    val threads: List<AgroDrop> = emptyList(),
    /** Unread drops per sender, for the badge on each row. */
    val unreadByFriend: Map<String, Int> = emptyMap(),
    /** The thread currently open, or null while the list is showing. */
    val openWith: String? = null,
    /** Everything exchanged with [openWith], oldest first. */
    val conversation: List<AgroDrop> = emptyList(),
    val unread: Int = 0,
    /**
     * True only for the very first load.
     *
     * Room answers immediately with whatever was cached, so a spinner after that would flash on
     * every refresh over data that is already on screen.
     */
    val loading: Boolean = true,
    /** The drop currently being looked up, so its row can say so instead of seeming inert. */
    val resolving: String? = null,
    /** This account's username, so a message can be told from a reply. */
    val me: String = ""
) {
    /** Who an exchange is with: whoever is not this account. */
    fun counterpart(drop: AgroDrop): String =
        if (drop.fromUser.equals(me, ignoreCase = true)) drop.toUser else drop.fromUser

    /** True when this account sent it, which is what decides the side a bubble sits on. */
    fun isMine(drop: AgroDrop): Boolean = drop.fromUser.equals(me, ignoreCase = true)
}
