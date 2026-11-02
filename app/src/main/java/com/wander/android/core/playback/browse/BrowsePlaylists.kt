package com.wander.android.core.playback.browse

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.RemotePlaylistDao
import com.wander.android.core.database.entity.RemotePlaylistEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.sources.IMusicSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The playlists a car is offered, read from Room: Wanda's own, then each connected backend's
 * copy in `remote_playlists`.
 *
 * A backend playlist is offered only once it has been opened in the app, because only then does
 * Room hold its track ids; one that never was would list nothing in the car. A backend that has
 * been signed out of keeps its rows in Room but is not offered, as the library does not show it.
 */
@Singleton
internal class BrowsePlaylists @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val remoteDao: RemotePlaylistDao,
    private val sources: Set<@JvmSuppressWildcards IMusicSource>
) {

    /** What a car shows for a playlist, and the track ids it plays, in order. */
    class Entry(val id: String, val name: String, val coverArtUrl: String?, val trackIds: List<String>)

    suspend fun all(): List<Entry> {
        val own = playlistDao.getAllPlaylists().map { Entry(it.id, it.name, it.coverArtUrl, ids(it.trackIds)) }
        val connected = connectedSources()
        val remote = remoteDao.getAll().filter { it.source in connected }.mapNotNull(::remoteEntry)
        return own + remote
    }

    /** The playlist [id] names, or null when it is not (or no longer) offered. */
    suspend fun byId(id: String): Entry? {
        playlistDao.getPlaylistById(id)?.let { return Entry(it.id, it.name, it.coverArtUrl, ids(it.trackIds)) }
        val stored = remoteDao.getById(id)?.takeIf { it.source in connectedSources() } ?: return null
        return remoteEntry(stored)
    }

    private fun remoteEntry(row: RemotePlaylistEntity): Entry? {
        val trackIds = row.storedTrackIds()?.takeIf { it.isNotEmpty() } ?: return null
        return Entry(row.id, row.name, row.coverArtUrl, trackIds)
    }

    private fun connectedSources(): Set<SourceType> =
        sources.filter { it.isConfigured.value }.mapTo(HashSet()) { it.sourceType }

    private fun ids(joined: String): List<String> = joined.split(',').filter { it.isNotBlank() }
}
