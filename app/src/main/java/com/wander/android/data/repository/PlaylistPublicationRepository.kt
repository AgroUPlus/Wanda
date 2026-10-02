package com.wander.android.data.repository

import android.content.Context
import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.core.database.dao.PlaylistPublicationDao
import com.wander.android.core.database.entity.PlaylistPublicationEntity
import com.wander.android.data.sources.agro.AgroPlaylistApi
import com.wander.android.data.sources.agro.PlaylistVisibility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/** A Wanda playlist's copy on the paired Agro server. */
data class PlaylistPublication(val agroId: String, val visibility: PlaylistVisibility)

/**
 * Which Wanda playlists have a copy on the paired Agro server, and who can open each copy.
 *
 * One copy per playlist: once published, sharing again sends the same link, so a playlist never
 * has several copies on the server that drift apart.
 *
 * Also carries the playlist screen's own outcomes — a file saved, a visibility changed, a write
 * that failed — to the app-wide snackbar, the way [ShareRepository.errors] does for sharing.
 */
@Singleton
class PlaylistPublicationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: PlaylistPublicationDao,
    private val agroPlaylists: AgroPlaylistApi
) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** [playlistId]'s Agro copy, or null when it has none. */
    fun publication(playlistId: String): Flow<PlaylistPublication?> =
        dao.observe(playlistId).map { row ->
            // A value written by a newer version reads as unpublished rather than crashing the screen.
            val visibility = PlaylistVisibility.entries.firstOrNull { it.name == row?.visibility }
            if (row == null || visibility == null) null else PlaylistPublication(row.agroId, visibility)
        }

    suspend fun record(playlistId: String, agroId: String, visibility: PlaylistVisibility) =
        dao.upsert(PlaylistPublicationEntity(playlistId, agroId, visibility.name))

    /** Changes the Agro copy's visibility, and keeps the local record only if the server agreed. */
    suspend fun changeVisibility(playlistId: String, visibility: PlaylistVisibility) {
        val publication = dao.get(playlistId) ?: return
        if (!agroPlaylists.isAvailable) {
            report(R.string.playlist_visibility_needs_agro)
            return
        }
        agroPlaylists.updateVisibility(publication.agroId, visibility).fold(
            onSuccess = {
                dao.setVisibility(playlistId, visibility.name)
                report(R.string.playlist_visibility_changed)
            },
            onFailure = { report(R.string.playlist_visibility_change_failed, it.message.orEmpty()) }
        )
    }

    /**
     * Deletes the Agro copy, after which its link opens nothing. The local record goes only once the
     * server has let go of the copy, so a failure leaves the playlist shown as shared, as it still is.
     */
    suspend fun unshare(playlistId: String) {
        val publication = dao.get(playlistId) ?: return
        if (!agroPlaylists.isAvailable) {
            report(R.string.playlist_visibility_needs_agro)
            return
        }
        agroPlaylists.delete(publication.agroId).fold(
            onSuccess = {
                dao.delete(playlistId)
                report(R.string.playlist_unshared)
            },
            onFailure = { report(R.string.playlist_unshare_failed, it.message.orEmpty()) }
        )
    }

    fun report(@StringRes message: Int, vararg args: Any) {
        _messages.tryEmit(context.getString(message, *args))
    }
}
