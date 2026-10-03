package com.wander.android.data.repository.sharedplaylist

/**
 * Where this device's copy of a shared playlist stands against the server — what the playlist
 * screen's sync indicator shows. Stored by name in `shared_playlists.syncState`.
 */
enum class SharedSyncState {
    /** Up to date as of the last check. */
    SYNCED,

    /** Holds edits made here that have not been sent yet. */
    PENDING,

    /** Talking to the server right now. */
    SYNCING,

    /** Known or likely to be behind: the last sync failed, typically for want of a connection. */
    OUT_OF_SYNC,

    /** Gone from the server, or no longer shared with this account. The copy stays, read-only. */
    REVOKED;

    companion object {
        fun of(name: String): SharedSyncState = entries.firstOrNull { it.name == name } ?: OUT_OF_SYNC
    }
}
