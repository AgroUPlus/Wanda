package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.work.PlaylistImportScheduler
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.RecordingArtwork
import com.wander.android.data.sources.agro.AgroSharedItem
import com.wander.android.data.sources.agro.AgroSharedPlaylist
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A track this device already has, offered as the match for a server item that names it. */
data class KnownTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)

/**
 * Writes what the server says a shared playlist is into Room, which is what every screen reads.
 *
 * The server knows tracks only by title and artist, so each item also needs a track on this
 * device to play as. An item seen before keeps the one it had; one this device just added gets the
 * track it was added from; anything else gets an `UNRESOLVED` placeholder that
 * `PlaylistImportWorker` matches in the background, exactly as for an imported playlist.
 *
 * For a playlist this account published, the Wanda playlist it came from is rewritten to match,
 * so edits made by collaborators show up in it like any other change.
 */
@Singleton
class SharedPlaylistMirror @Inject constructor(
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val importScheduler: PlaylistImportScheduler,
    private val artwork: RecordingArtwork
) {
    /**
     * Makes [server] the copy. [confirmed] are adds the server has just accepted from this device,
     * [known] tracks to match new items against first, and [remaining] edits still waiting to be
     * sent, which are laid back on top so they do not vanish from the screen meanwhile. [reflect]
     * is false only while reconciling, when the Wanda playlist still holds the order being sent.
     */
    suspend fun write(
        server: AgroSharedPlaylist,
        me: String,
        localPlaylistId: String?,
        state: SharedSyncState,
        confirmed: List<SharedPlaylistOp.Add> = emptyList(),
        known: List<KnownTrack> = emptyList(),
        remaining: List<SharedPlaylistOp> = emptyList(),
        reflect: Boolean = true
    ) {
        val before = dao.items(server.id).filterNot { it.isPending }
        val previous = before.associateBy { it.itemId }
        val adds = confirmed.toMutableList()
        // A blend is written afresh, every item under a new id, so an item that only changed id
        // keeps the track it had by name. Placeholders included: one still being matched is
        // swapped everywhere it is referenced once it is.
        val kept = server.items.mapTo(HashSet()) { it.id }
        val carried = before.filter { it.itemId !in kept }.map { KnownTrack(it.trackId, it.title, it.artist) }
        val candidates = (known + carried).toMutableList()
        val placeholders = mutableListOf<TrackEntity>()

        val mapped = server.items.mapIndexed { index, item ->
            val trackId = previous[item.id]?.trackId
                ?: adds.takeMatching(item, me)
                ?: candidates.takeMatching(item)
                ?: placeholder(item).also { placeholders += it }.id
            SharedPlaylistItemEntity(
                agroId = server.id,
                itemId = item.id,
                position = index,
                trackId = trackId,
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = item.durationMs,
                addedBy = item.addedBy,
                addedAt = item.addedAt
            )
        }
        val shown = remaining.fold(mapped) { items, op -> SharedPlaylistEdits.applyLocally(items, op, me) }
            .map { it.copy(agroId = server.id) }

        // Placeholders first, so no reader ever sees an item pointing at a track with no row.
        if (placeholders.isNotEmpty()) trackDao.upsertTracks(placeholders)
        lendCovers(mapped.map { it.trackId } - placeholders.map { it.id }.toSet())
        dao.replaceItems(server.id, shown)
        dao.upsert(
            SharedPlaylistEntity(
                agroId = server.id,
                localPlaylistId = localPlaylistId,
                title = server.title,
                description = server.description,
                ownerId = server.ownerId,
                visibility = server.visibility.name,
                editAccess = server.editAccess.name,
                myRole = server.myRole.name,
                revision = server.revision,
                syncState = state.name,
                lastSyncedAt = System.currentTimeMillis(),
                publishedAt = dao.get(server.id)?.publishedAt ?: System.currentTimeMillis(),
                isBlend = server.isBlend
            )
        )
        if (reflect) localPlaylistId?.let { reflectInto(it, server.title, shown) }
        if (placeholders.isNotEmpty()) importScheduler.enqueue(localPlaylistId ?: routeId(server.id))
    }

    /** Rewrites the published Wanda playlist to hold what the shared copy holds. */
    suspend fun reflectInto(localPlaylistId: String, title: String, items: List<SharedPlaylistItemEntity>) {
        val local = playlistDao.getPlaylistById(localPlaylistId) ?: return
        val trackIds = items.joinToString(",") { it.trackId }
        if (local.trackIds == trackIds && local.name == title) return
        playlistDao.updatePlaylist(local.copy(name = title, trackIds = trackIds, updatedAt = System.currentTimeMillis()))
    }

    /**
     * The server sends no artwork, so a placeholder borrows the cover of the same song — or the
     * same album — already in the library. `PlaylistImportWorker` swaps in the real track later.
     */
    private suspend fun placeholder(item: AgroSharedItem): TrackEntity {
        val album = item.album?.takeIf { it.isNotBlank() }
        return TrackEntity.fromUnifiedTrack(
            UnifiedTrack(
                id = SourceType.UNRESOLVED.idPrefix + UUID.randomUUID(),
                source = SourceType.UNRESOLVED,
                title = item.title,
                artist = item.artist,
                album = album,
                durationMs = item.durationMs,
                artworkUrl = artwork.coverFor(item.title, item.artist, album)
            )
        )
    }

    /**
     * Placeholders made before covers were borrowed, or before the library held the song, get one
     * now. Only coverless placeholders are looked at, so a settled playlist costs one read.
     */
    private suspend fun lendCovers(trackIds: List<String>) {
        val unresolved = trackIds.filter { it.startsWith(SourceType.UNRESOLVED.idPrefix) }
        if (unresolved.isEmpty()) return
        trackDao.getTracksByIds(unresolved)
            .filter { it.artworkUrl.isNullOrBlank() }
            .forEach { track ->
                artwork.coverFor(track.title, track.artist, track.album)?.let { trackDao.setArtwork(track.id, it) }
            }
    }

    private fun MutableList<SharedPlaylistOp.Add>.takeMatching(item: AgroSharedItem, me: String): String? {
        if (!item.addedBy.equals(me, ignoreCase = true)) return null
        val index = indexOfFirst { same(it.title, item.title) && same(it.artist, item.artist) }
        return if (index >= 0) removeAt(index).trackId else null
    }

    private fun MutableList<KnownTrack>.takeMatching(item: AgroSharedItem): String? {
        val index = indexOfFirst { same(it.title, item.title) && same(it.artist, item.artist) }
        return if (index >= 0) removeAt(index).trackId else null
    }

    companion object {
        /** The id a followed playlist is opened by: its Agro id under the `agro:` prefix. */
        fun routeId(agroId: String): String = SourceType.AGRO.idPrefix + agroId

        /** The Agro id inside a [routeId], or null when [id] is not one. */
        fun agroIdOf(id: String): String? = id.removePrefix(SourceType.AGRO.idPrefix).takeIf { it != id }

        fun same(a: String, b: String): Boolean =
            a.trim().lowercase(Locale.ROOT) == b.trim().lowercase(Locale.ROOT)
    }
}
