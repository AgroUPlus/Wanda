package com.wander.android.ui.screens.library

import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.sharedplaylist.SharedWithMeRepository
import com.wander.android.data.sources.agro.AgroSharedListing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
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
    private val sharedWithMe: SharedWithMeRepository,
    private val sharedDao: SharedPlaylistDao,
    private val playlistDao: PlaylistDao
) {
    private val roomBacked = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    private val remote = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())

    private val _playlists = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    val playlists: StateFlow<List<UnifiedPlaylist>> = _playlists.asStateFlow()

    private val _shared = MutableStateFlow<SharedWithMe>(SharedWithMe.NotLoaded)
    val shared: StateFlow<SharedWithMe> = _shared.asStateFlow()

    val canListShared: Boolean get() = sharedWithMe.isAvailable

    /** Starts following Room. Called once, from the owning view model's scope. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            merge(sharedDao.observeAll().map { }, playlistDao.observeVersions().map { })
                // A sync writes a copy in several steps; one re-read for the lot.
                .debounce(ROOM_SETTLE_MS)
                .collect { readRoomBacked() }
        }
    }

    /** Both halves, and the shared list when it has been opened. */
    suspend fun refresh() {
        readRoomBacked()
        remote.value = musicRepository.getPlaylists(only = SourceType.entries.toSet() - ROOM_BACKED)
        publish()
        if (_shared.value != SharedWithMe.NotLoaded) loadShared()
    }

    suspend fun loadShared() {
        if (_shared.value == SharedWithMe.NotLoaded) _shared.value = SharedWithMe.Loading
        _shared.value = sharedWithMe.list().fold(
            onSuccess = { SharedWithMe.Loaded(it) },
            onFailure = { SharedWithMe.Failed(it.message.orEmpty()) }
        )
    }

    suspend fun open(listing: AgroSharedListing): Result<String> = sharedWithMe.open(listing).onSuccess {
        // Following it changed what is shared and followed: say so in the list at once.
        _shared.update { state ->
            if (state is SharedWithMe.Loaded) {
                SharedWithMe.Loaded(state.items.map { if (it.id == listing.id) it.copy(isFollowing = true) else it })
            } else {
                state
            }
        }
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

/** Where the "Shared with me" list stands. */
sealed interface SharedWithMe {
    data object NotLoaded : SharedWithMe
    data object Loading : SharedWithMe
    data class Loaded(val items: List<AgroSharedListing>) : SharedWithMe
    data class Failed(val message: String) : SharedWithMe
}
