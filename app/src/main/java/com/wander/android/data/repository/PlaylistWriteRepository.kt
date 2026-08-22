package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
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
    private val sources: Set<@JvmSuppressWildcards IMusicSource>
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

    /** Whether tracks from [type] can be put in a playlist at all. Drives the UI's action list. */
    fun canWrite(type: SourceType): Boolean = writableSource(type) != null

    /**
     * Playlists that could receive [type]'s tracks.
     *
     * Filtered by source, not merged: adding a Navidrome track to a YouTube Music playlist is not
     * something any backend here can do, so offering it would be inviting a failure.
     */
    suspend fun writableTargets(type: SourceType): List<UnifiedPlaylist> =
        withContext(Dispatchers.IO) {
            writableSource(type)?.getPlaylists()?.getOrDefault(emptyList()).orEmpty()
        }

    suspend fun createPlaylist(
        type: SourceType,
        name: String,
        trackIds: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        val source = writableSource(type)
            ?: return@withContext Result.failure(
                IllegalStateException("$type cannot create playlists")
            )
        source.createPlaylist(name.trim(), trackIds)
            .onSuccess { _messages.tryEmit("Created \"${name.trim()}\".") }
            .onFailure { _messages.tryEmit(it.message ?: "Couldn't create that playlist.") }
    }

    suspend fun addToPlaylist(
        playlist: UnifiedPlaylist,
        trackIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val source = writableSource(playlist.source)
            ?: return@withContext Result.failure(
                IllegalStateException("${playlist.source} cannot modify playlists")
            )
        source.addToPlaylist(playlist.id, trackIds)
            .onSuccess { _messages.tryEmit("Added to \"${playlist.name}\".") }
            .onFailure { _messages.tryEmit(it.message ?: "Couldn't add to that playlist.") }
    }
}
