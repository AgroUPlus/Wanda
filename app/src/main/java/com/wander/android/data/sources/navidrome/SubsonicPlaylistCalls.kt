package com.wander.android.data.sources.navidrome

// Playlist writes on the Subsonic API, kept apart from [SubsonicApiClient]'s reads. Each goes
// through [SubsonicApiClient.call], so auth and error mapping stay in one place.

/**
 * Creates a playlist and returns its id.
 *
 * Subsonic overloads this endpoint: passing `playlistId` *replaces* an existing playlist
 * rather than making a new one, so only `name` is ever sent here. Older servers answer with an
 * empty body instead of the new playlist, which is why the id is looked up by name as a
 * fallback rather than assumed — see [NavidromeSource].
 */
suspend fun SubsonicApiClient.createPlaylist(name: String, songIds: List<String>): Result<String?> =
    call(
        "createPlaylist.view",
        "name" to name,
        *songIds.map { "songId" to it as Any? }.toTypedArray()
    ).map { it.playlist?.id }

/**
 * Appends tracks to an existing playlist.
 *
 * `updatePlaylist` adds rather than replaces, so this is safe to call repeatedly; the server
 * keeps duplicates if the same track is added twice, which matches what the web UI does.
 */
suspend fun SubsonicApiClient.updatePlaylist(playlistId: String, songIdsToAdd: List<String>): Result<Unit> =
    call(
        "updatePlaylist.view",
        "playlistId" to playlistId,
        *songIdsToAdd.map { "songIdToAdd" to it as Any? }.toTypedArray()
    ).map { }

/** Sets the name and the comment; an empty comment clears the one the server holds. */
suspend fun SubsonicApiClient.updatePlaylistDetails(playlistId: String, name: String, comment: String): Result<Unit> =
    call("updatePlaylist.view", "playlistId" to playlistId, "name" to name, "comment" to comment).map { }

/** Drops the entry at [index] (zero-based, as the server lists them) — one copy, not every one. */
suspend fun SubsonicApiClient.removeFromPlaylist(playlistId: String, index: Int): Result<Unit> =
    call("updatePlaylist.view", "playlistId" to playlistId, "songIndexToRemove" to index).map { }

/**
 * Replaces the playlist's songs with [songIds], in that order. Subsonic has no "move", so a
 * reorder is this: `createPlaylist` given an existing `playlistId` overwrites its entries.
 */
suspend fun SubsonicApiClient.replacePlaylistSongs(playlistId: String, songIds: List<String>): Result<Unit> =
    call(
        "createPlaylist.view",
        "playlistId" to playlistId,
        *songIds.map { "songId" to it as Any? }.toTypedArray()
    ).map { }

suspend fun SubsonicApiClient.deletePlaylist(playlistId: String): Result<Unit> =
    call("deletePlaylist.view", "id" to playlistId).map { }
