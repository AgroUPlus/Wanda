package com.wander.android.ui.screens.library

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The library's playlists, kept current on their own.
 *
 * Two halves, read differently. Wanda's own playlists and Agro's copies live in Room, so they are
 * re-read whenever Room says one changed — made, deleted, unfollowed, or taken away by its owner —
 * and the list follows without a pull. A backend's playlists are a network call, made only on a
 * refresh. Neither waits on the rest of the library loading.
 */
@OptIn(FlowPreview::class)
class LibraryPlaylistsLoader @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sharedDao: SharedPlaylistDao,
    private val playlistDao: PlaylistDao
) {
    private val roomBacked = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    private val remote = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())

    private val _playlists = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    val playlists: StateFlow<List<UnifiedPlaylist>> = _playlists.asStateFlow()

    /** Starts following Room. Called once, from the owning view model's scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            merge(sharedDao.observeAll().map { }, playlistDao.observeVersions().map { })
                // A sync writes a copy in several steps; one re-read for the lot.
                .debounce(ROOM_SETTLE_MS)
                .collect { readRoomBacked() }
        }
    }

    /** Both halves. */
    suspend fun refresh() {
        readRoomBacked()
        remote.value = musicRepository.getPlaylists(only = SourceType.entries.toSet() - ROOM_BACKED)
        publish()
    }

    private suspend fun readRoomBacked() {
        roomBacked.value = musicRepository.getPlaylists(only = ROOM_BACKED)
        publish()
    }

    /** In source order, as the list has always been, Wanda's own and Agro's in their place. */
    private fun publish() {
        _playlists.value = (roomBacked.value + remote.value).sortedBy { it.source.ordinal }
    }

    private companion object {
        val ROOM_BACKED = setOf(SourceType.LOCAL, SourceType.AGRO)
        const val ROOM_SETTLE_MS = 300L
    }
}
