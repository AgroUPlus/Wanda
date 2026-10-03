package com.wander.android.data.repository

import android.content.Context
import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.sharedplaylist.KnownTrack
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistMirror
import com.wander.android.data.repository.sharedplaylist.SharedSyncState
import com.wander.android.data.sources.agro.AgroPlaylistApi
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/** A Wanda playlist's copy on the paired Agro server: who can open it, and who can change it. */
data class PlaylistPublication(
    val agroId: String,
    val visibility: PlaylistVisibility,
    val editAccess: EditAccess = EditAccess.OFF
)

/**
 * Which Wanda playlists have a copy on the paired Agro server, and the owner's controls over it.
 *
 * One copy per playlist: sharing again sends the same link, and the copy is kept in step with the
 * playlist from then on — see `SharedPlaylistSync` — so whoever opens the link gets the playlist as
 * it is now rather than as it was when it was first shared.
 *
 * Also carries the playlist screen's own outcomes — a file saved, a visibility changed, a write
 * that failed — to the app-wide snackbar, the way [ShareRepository.errors] does for sharing.
 */
@Singleton
class PlaylistPublicationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val agroPlaylists: AgroPlaylistApi,
    private val shared: AgroSharedPlaylistApi,
    private val mirror: SharedPlaylistMirror
) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** [playlistId]'s Agro copy, or null when it has none. */
    fun publication(playlistId: String): Flow<PlaylistPublication?> =
        dao.observeForLocal(playlistId).map { row -> row?.toPublication() }

    /**
     * Remembers that [playlistId] was just published as [agroId], and reads the copy back so it is
     * kept in step from now on. The tracks went up in [tracks]' order, so each item is matched to
     * the track it was sent from.
     */
    suspend fun record(playlistId: String, agroId: String, visibility: PlaylistVisibility, tracks: List<UnifiedTrack>) {
        dao.upsert(
            SharedPlaylistEntity(
                agroId = agroId,
                localPlaylistId = playlistId,
                title = "",
                ownerId = shared.me,
                visibility = visibility.name,
                myRole = "OWNER",
                syncState = SharedSyncState.SYNCING.name
            )
        )
        shared.fetch(agroId).fold(
            onSuccess = { server ->
                val known = tracks.map { KnownTrack(it.id, it.title, it.artist, it.album, it.durationMs) }
                mirror.write(server, shared.me, playlistId, SharedSyncState.SYNCED, known = known)
            },
            // The copy exists either way; the first sync reads it back and finishes the job.
            onFailure = { dao.setState(agroId, SharedSyncState.OUT_OF_SYNC.name) }
        )
    }

    /** Changes who can open the Agro copy, and keeps the local record only if the server agreed. */
    suspend fun changeVisibility(playlistId: String, visibility: PlaylistVisibility) {
        val row = available(playlistId) ?: return
        agroPlaylists.updateVisibility(row.agroId, visibility).fold(
            onSuccess = {
                // The server narrows who can edit in the same write; mirror that rather than wait.
                val access = editAccessOf(row).clampedTo(visibility)
                dao.setAccess(row.agroId, visibility.name, access.name)
                report(R.string.playlist_visibility_changed)
            },
            onFailure = { report(R.string.playlist_visibility_change_failed, it.message.orEmpty()) }
        )
    }

    /** Changes who besides the owner may edit, keeping what the server actually stored. */
    suspend fun changeEditAccess(playlistId: String, access: EditAccess) {
        val row = available(playlistId) ?: return
        shared.updateEditAccess(row.agroId, access).fold(
            onSuccess = { stored ->
                dao.setAccess(row.agroId, row.visibility, stored.name)
                report(if (stored == EditAccess.OFF) R.string.playlist_collab_turned_off else R.string.playlist_collab_changed)
            },
            onFailure = { report(R.string.playlist_collab_change_failed, it.message.orEmpty()) }
        )
    }

    /**
     * Deletes the Agro copy, after which its link opens nothing and followers see it as no longer
     * shared. The local record goes only once the server has let go of the copy.
     */
    suspend fun unshare(playlistId: String) {
        val row = available(playlistId) ?: return
        agroPlaylists.delete(row.agroId).fold(
            onSuccess = {
                dao.forget(row.agroId)
                trackDao.deleteUnreferencedUnresolved()
                report(R.string.playlist_unshared)
            },
            onFailure = { report(R.string.playlist_unshare_failed, it.message.orEmpty()) }
        )
    }

    fun report(@StringRes message: Int, vararg args: Any) {
        _messages.tryEmit(context.getString(message, *args))
    }

    private suspend fun available(playlistId: String): SharedPlaylistEntity? {
        val row = dao.forLocal(playlistId) ?: return null
        if (!agroPlaylists.isAvailable) {
            report(R.string.playlist_visibility_needs_agro)
            return null
        }
        return row
    }

    private fun editAccessOf(row: SharedPlaylistEntity) =
        EditAccess.entries.firstOrNull { it.name == row.editAccess } ?: EditAccess.OFF

    private fun SharedPlaylistEntity.toPublication(): PlaylistPublication? {
        // A value written by a newer version reads as unpublished rather than crashing the screen.
        val level = PlaylistVisibility.entries.firstOrNull { it.name == visibility } ?: return null
        return PlaylistPublication(agroId, level, editAccessOf(this))
    }
}
