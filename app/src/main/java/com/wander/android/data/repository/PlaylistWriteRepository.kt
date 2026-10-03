package com.wander.android.data.repository

import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.sharedplaylist.EditRefusal
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistEditor
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRunner
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creating playlists and adding tracks to them.
 *
 * Separate from [MusicRepository] rather than bolted onto it: that class is already over the file
 * budget, and playlist *writes* are a different job from the library reads it exists for. Reads
 * stay there — `getPlaylists` fans out across sources — while everything here targets exactly one
 * source, because a playlist lives on a server and cannot straddle two.
 */
@Singleton
class PlaylistWriteRepository @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards IMusicSource>,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val shared: SharedPlaylistRepository,
    private val sharedEditor: SharedPlaylistEditor,
    private val runner: SharedPlaylistRunner,
    private val outcomes: PlaylistPublicationRepository
) {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Outcomes worth telling the user about, merged into the app's snackbar by
     * `WanderAppViewModel` the same way library-sync and share errors are. A playlist write
     * happens off-screen — the sheet is already gone by the time the server answers — so silence
     * would leave the user with no idea whether it worked.
     */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun writableSource(type: SourceType): IMusicSource? = sources.firstOrNull {
        it.sourceType == type && it.isConfigured.value && it.capabilities.playlistWrite
    }

    /** Whether tracks from [type] can be put in a playlist at all (either on its source or in local universal playlists). */
    fun canWrite(type: SourceType): Boolean =
        writableSource(type) != null || writableSource(SourceType.LOCAL) != null

    /**
     * Playlists that can receive [type]'s tracks: local universal playlists (which accept tracks
     * from any source), any playlist on [type]'s own backend if writable, and the shared playlists
     * this account follows and may add to.
     */
    suspend fun writableTargets(type: SourceType): List<UnifiedPlaylist> =
        withContext(Dispatchers.IO) {
            val local = writableSource(SourceType.LOCAL)?.getPlaylists()?.getOrDefault(emptyList()).orEmpty()
            val specific = if (type != SourceType.LOCAL) {
                writableSource(type)?.getPlaylists()?.getOrDefault(emptyList()).orEmpty()
            } else emptyList()
            (local + specific + shared.writableFollowed()).distinctBy { it.id }
        }

    suspend fun createPlaylist(
        type: SourceType,
        name: String,
        trackIds: List<String>,
        tracks: List<UnifiedTrack> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val source = writableSource(type)
            ?: return@withContext Result.failure(
                IllegalStateException("$type cannot create playlists")
            )
        if (type == SourceType.LOCAL) persistForUniversalPlaylist(tracks)
        source.createPlaylist(name.trim(), trackIds)
            .onSuccess { _messages.tryEmit("Created \"${name.trim()}\".") }
            .onFailure { _messages.tryEmit(it.message ?: "Couldn't create that playlist.") }
    }

    /**
     * A local playlist stores bare id strings, and the read path resolves them through Room. A
     * track that only ever existed as a search hit has no row there, so adding it to a universal
     * playlist would leave an id that resolves to nothing and the track would silently vanish from
     * the playlist. Persist it first, exactly as `MusicRepository.toggleLike` does for a like.
     */
    private suspend fun persistForUniversalPlaylist(tracks: List<UnifiedTrack>) {
        if (tracks.isEmpty()) return
        trackDao.upsertTracks(tracks.map { TrackEntity.fromUnifiedTrack(it) })
    }

    suspend fun addToPlaylist(
        playlist: UnifiedPlaylist,
        trackIds: List<String>,
        tracks: List<UnifiedTrack> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Shared through Agro, followed or published from here: the change goes to everyone.
        shared.editTarget(playlist.id)?.let { agroId ->
            val toAdd = tracks.ifEmpty { trackDao.getTracksByIds(trackIds).map(TrackEntity::toUnifiedTrack) }
            return@withContext sharedOutcome(sharedEditor.add(agroId, toAdd), R.string.shared_playlist_added, playlist.name)
        }
        val source = writableSource(playlist.source)
            ?: return@withContext Result.failure(
                IllegalStateException("${playlist.source} cannot modify playlists")
            )
        if (playlist.source == SourceType.LOCAL) persistForUniversalPlaylist(tracks)
        source.addToPlaylist(playlist.id, trackIds)
            .onSuccess { syncIfPublished(playlist.id) }
            .onSuccess { _messages.tryEmit("Added to \"${playlist.name}\".") }
            .onFailure { _messages.tryEmit(it.message ?: "Couldn't add to that playlist.") }
    }

    suspend fun deletePlaylist(playlist: UnifiedPlaylist): Result<Unit> = withContext(Dispatchers.IO) {
        val source = writableSource(playlist.source)
            ?: return@withContext Result.failure(
                IllegalStateException("${playlist.source} cannot delete playlists")
            )
        source.deletePlaylist(playlist.id)
            .onSuccess { _messages.tryEmit("Deleted \"${playlist.name}\".") }
            .onFailure { _messages.tryEmit(it.message ?: "Couldn't delete that playlist.") }
    }

    /** Removes the track at [index]. A shared playlist checks this account may; a Wanda one always may. */
    suspend fun removeFromPlaylist(playlist: UnifiedPlaylist, index: Int): Result<Unit> = withContext(Dispatchers.IO) {
        shared.editTarget(playlist.id)?.let { agroId ->
            return@withContext sharedOutcome(sharedEditor.remove(agroId, index), R.string.shared_playlist_removed, playlist.name)
        }
        rewriteLocal(playlist.id) { ids -> ids.toMutableList().apply { if (index in indices) removeAt(index) } }
    }

    /** Moves the track at [from] to [to]. */
    suspend fun moveInPlaylist(playlist: UnifiedPlaylist, from: Int, to: Int): Result<Unit> = withContext(Dispatchers.IO) {
        shared.editTarget(playlist.id)?.let { agroId ->
            return@withContext sharedOutcome(sharedEditor.move(agroId, from, to), null, playlist.name)
        }
        rewriteLocal(playlist.id) { ids ->
            ids.toMutableList().apply { if (from in indices && to in indices) add(to, removeAt(from)) }
        }
    }

    private suspend fun rewriteLocal(playlistId: String, change: (List<String>) -> List<String>): Result<Unit> {
        val local = playlistDao.getPlaylistById(playlistId)
            ?: return Result.failure(IllegalStateException("No such playlist"))
        val ids = local.trackIds.split(',').filter { it.isNotBlank() }
        playlistDao.updatePlaylist(
            local.copy(trackIds = change(ids).joinToString(","), updatedAt = System.currentTimeMillis())
        )
        trackDao.deleteUnreferencedUnresolved()
        syncIfPublished(playlistId)
        return Result.success(Unit)
    }

    /** A published playlist edited before its copy caught up: the catch-up sync carries the edit. */
    private suspend fun syncIfPublished(playlistId: String) {
        shared.agroIdFor(playlistId)?.let(runner::syncSoon)
    }

    private fun sharedOutcome(refusal: EditRefusal?, @StringRes done: Int?, name: String): Result<Unit> {
        if (refusal == null) {
            done?.let { outcomes.report(it, name) }
            return Result.success(Unit)
        }
        outcomes.report(
            when (refusal) {
                EditRefusal.NOT_ALLOWED -> R.string.shared_playlist_not_allowed
                EditRefusal.PENDING_ITEM -> R.string.shared_playlist_wait_for_sync
                EditRefusal.NO_SUCH_ITEM -> R.string.shared_playlist_changed_meanwhile
            }
        )
        return Result.failure(IllegalStateException(refusal.name))
    }
}
