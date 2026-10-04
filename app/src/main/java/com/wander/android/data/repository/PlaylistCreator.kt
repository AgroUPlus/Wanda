package com.wander.android.data.repository

import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroPlaylistApi
import com.wander.android.data.sources.agro.AgroPlaylistTrack
import com.wander.android.data.sources.agro.BlendRecipe
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bringing a playlist into existence, of whichever kind the new-playlist sheet was set to.
 *
 * Each answers the id to open the new playlist by, and fails only when nothing usable was made.
 */
@Singleton
internal class PlaylistCreator @Inject constructor(
    private val writes: PlaylistWriteRepository,
    private val agroPlaylists: AgroPlaylistApi,
    private val publications: PlaylistPublicationRepository,
    private val blends: BlendRepository
) {
    /** A playlist on [source] — a universal one when that is [SourceType.LOCAL]. */
    suspend fun plain(name: String, source: SourceType, tracks: List<UnifiedTrack>): Result<String> =
        writes.createPlaylist(source, name, tracks.map { it.id }, tracks)

    /**
     * A universal playlist published to Agro straight away, open to [visibility] and editable by
     * [access]. Published directly rather than through the share sheet's path: nobody asked to
     * send it anywhere yet, only for it to be shared.
     *
     * When the publish fails the playlist still exists here, so it is kept and the refusal is
     * reported app-wide rather than undone — it can be shared again from its own screen.
     */
    suspend fun collaborative(
        name: String,
        visibility: PlaylistVisibility,
        access: EditAccess,
        tracks: List<UnifiedTrack>
    ): Result<String> {
        val local = tracks.filter { it.source != SourceType.UNRESOLVED }
        val id = writes.createPlaylist(SourceType.LOCAL, name, local.map { it.id }, local)
            .getOrElse { return Result.failure(it) }
        val agroId = agroPlaylists.publish(
            name,
            local.map { AgroPlaylistTrack(it.title, it.artist, it.album, it.durationMs) },
            visibility
        ).getOrElse {
            publications.report(R.string.new_playlist_created_not_shared, it.message.orEmpty())
            return Result.success(id)
        }
        publications.record(id, agroId, visibility, local)
        if (access != EditAccess.OFF) publications.changeEditAccess(id, access.clampedTo(visibility))
        return Result.success(id)
    }

    suspend fun blend(name: String, members: List<String>, recipe: BlendRecipe): Result<String> =
        blends.create(name, members, recipe)
}
