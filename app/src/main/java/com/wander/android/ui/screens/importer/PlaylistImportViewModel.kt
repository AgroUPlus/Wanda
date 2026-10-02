package com.wander.android.ui.screens.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.repository.PlaylistImportRepository
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Spotify, Deezer and Apple Music are read from a link to a public playlist, with no session at
 * all — the guide step tells the person how to make theirs public. YouTube is the one platform
 * this app already holds an account for elsewhere (see [GoogleAccountManager], signed in from
 * Settings), so it alone also gets a "browse my library" option.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val importRepository: PlaylistImportRepository,
    private val googleAccountManager: GoogleAccountManager,
    private val parserCoordinator: PlaylistParserCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistImportUiState())
    val state: StateFlow<PlaylistImportUiState> = _state.asStateFlow()

    val progress: StateFlow<ImportProgress> = importRepository.progress
    val isYouTubeLoggedIn: StateFlow<Boolean> = googleAccountManager.isLoggedIn

    private fun isLoggedInElsewhere(platform: PlatformType): Boolean =
        platform == PlatformType.YOUTUBE && googleAccountManager.isLoggedIn.value

    fun selectPlatform(platform: PlatformType) {
        _state.value = _state.value.copy(
            platform = platform,
            discoveredPlaylists = emptyList(),
            hasCheckedDiscovery = false,
            discoveryDismissed = false,
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            manualInput = "",
            error = null,
            mismatchedPlatform = null
        )
        if (isLoggedInElsewhere(platform)) checkOwnAccountPlaylists(platform)
    }

    fun backToPlatformPicker() {
        _state.value = _state.value.copy(platform = null, error = null)
    }

    fun clearLoadedPlaylist() {
        _state.value = _state.value.copy(
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            error = null
        )
    }

    /** Called on returning from Settings' YouTube sign-in — picks the account state back up. */
    fun recheckSessions() {
        val platform = _state.value.platform ?: return
        if (!_state.value.hasCheckedDiscovery && isLoggedInElsewhere(platform)) {
            checkOwnAccountPlaylists(platform)
        }
    }

    /** Drops the discovered library grid so the direct-link form shows instead. */
    fun switchToDirectLink() {
        _state.value = _state.value.copy(discoveredPlaylists = emptyList(), discoveryDismissed = true)
    }

    fun checkYouTubePlaylists() = checkOwnAccountPlaylists(PlatformType.YOUTUBE)

    private fun checkOwnAccountPlaylists(platform: PlatformType) {
        if (platform != PlatformType.YOUTUBE) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true)
            parserCoordinator.fetchYouTubePlaylists().onSuccess { lists ->
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    hasCheckedDiscovery = true,
                    discoveredPlaylists = lists,
                    error = null
                )
            }.onFailure { err ->
                _state.value = _state.value.copy(isDiscovering = false, hasCheckedDiscovery = true, error = err.message)
            }
        }
    }

    fun setManualInput(input: String) {
        _state.value = _state.value.copy(manualInput = input, error = null, mismatchedPlatform = null)
    }

    fun loadPlaylist(url: String, fallbackTitle: String? = null, fallbackCover: String? = null) {
        val chosen = _state.value.platform
        val pasted = PlatformType.detect(url)
        if (chosen != null && chosen != PlatformType.PLAIN_TEXT &&
            pasted != PlatformType.PLAIN_TEXT && pasted != chosen
        ) {
            _state.value = _state.value.copy(mismatchedPlatform = pasted, error = null)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlaylist = true, error = null, mismatchedPlatform = null)
            parserCoordinator.parsePlaylist(url, fallbackTitle, fallbackCover).onSuccess { updated ->
                _state.value = _state.value.copy(
                    isLoadingPlaylist = false,
                    loadedPlaylist = updated,
                    selectedIndices = updated.tracks.indices.toSet(),
                    error = null
                )
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    isLoadingPlaylist = false,
                    error = err.message ?: "Failed to read playlist"
                )
            }
        }
    }

    fun toggleTrack(index: Int) {
        val current = _state.value.selectedIndices.toMutableSet()
        if (current.contains(index)) current.remove(index) else current.add(index)
        _state.value = _state.value.copy(selectedIndices = current)
    }

    fun selectAll() {
        val size = _state.value.loadedPlaylist?.tracks?.size ?: 0
        _state.value = _state.value.copy(selectedIndices = (0 until size).toSet())
    }

    fun deselectAll() {
        _state.value = _state.value.copy(selectedIndices = emptySet())
    }

    fun startImport(customTitle: String? = null) {
        val playlist = _state.value.loadedPlaylist ?: return
        val selected = _state.value.selectedIndices
        val filteredTracks = playlist.tracks.filterIndexed { idx, _ -> selected.contains(idx) }
        if (filteredTracks.isEmpty()) return

        viewModelScope.launch {
            importRepository.importParsedPlaylist(
                rawPlaylist = playlist,
                customTitle = customTitle,
                tracksToImport = filteredTracks
            )
        }
    }

    fun reset() {
        importRepository.reset()
        _state.value = _state.value.copy(
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            error = null
        )
    }
}
