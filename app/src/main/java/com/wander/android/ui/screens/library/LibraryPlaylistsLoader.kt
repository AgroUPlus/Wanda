package com.wander.android.ui.screens.library

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.network.userMessageRes
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
 * Two halves, both out of Room. Wanda's own playlists and Agro's copies are re-read whenever Room
 * says one changed — made, deleted, unfollowed, or taken away by its owner. A backend's playlists
 * are Room's copy of its last listing, followed as a flow: a refresh asks the backends and writes
 * what they answer, and the list follows — so they are still listed offline. Neither waits on the
 * rest of the library loading.
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

    /** A @StringRes saying why the last read failed, or null. A failed half keeps what it showed before. */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    /** Starts following Room. Called once, from the owning view model's scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            merge(sharedDao.observeAll().map { }, playlistDao.observeVersions().map { })
                // A sync writes a copy in several steps; one re-read for the lot.
                .debounce(ROOM_SETTLE_MS)
                .collect { readRoomBacked() }
        }
        scope.launch {
            musicRepository.observeRemotePlaylists().collect { stored ->
                // Kept in Room through a sign-out, but not shown for a backend no longer connected.
                val connected = musicRepository.sources
                    .filter { it.isConfigured.value }
                    .mapTo(HashSet()) { it.sourceType }
                remote.value = stored.filter { it.source in connected }
                publish()
            }
        }
    }

    /** Both halves; the backends' answer reaches the list through Room. */
    suspend fun refresh() {
        readRoomBacked()
        musicRepository.getPlaylists(only = SourceType.entries.toSet() - ROOM_BACKED)
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.userMessageRes() }
    }

    private suspend fun readRoomBacked() {
        musicRepository.getPlaylists(only = ROOM_BACKED)
            .onSuccess { roomBacked.value = it }
            .onFailure { _error.value = it.userMessageRes() }
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
