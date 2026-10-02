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

/**
 * Which Wanda playlists have a copy on the paired Agro server, and who can open each copy.
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

    /** Who can open [playlistId]'s Agro copy, or null when it was never published. */
    fun visibility(playlistId: String): Flow<PlaylistVisibility?> =
        dao.observe(playlistId).map { row -> row?.visibility?.let(::parse) }

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

    fun report(@StringRes message: Int, vararg args: Any) {
        _messages.tryEmit(context.getString(message, *args))
    }

    // A value written by a newer version reads as unknown rather than crashing the screen.
    private fun parse(name: String) = PlaylistVisibility.entries.firstOrNull { it.name == name }
}
