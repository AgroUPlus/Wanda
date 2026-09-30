package com.wander.android.ui.screens.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.importer.AppleMusicPlaylistParser
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import com.wander.android.data.repository.PlaylistImportRepository
import com.wander.android.data.sources.deezer.DeezerAccountManager
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Spotify's parser (see [SpotifyPlaylistParser]) and Apple Music's read a playlist by its share
 * link with no session at all — that covers the public playlists someone would actually paste in
 * here, but not a private one.
 *
 * YouTube and Deezer are the two platforms this app already holds an account for elsewhere (see
 * [GoogleAccountManager] and [DeezerAccountManager], both signed in from Settings), so they alone
 * get a "browse my library" option — no separate sign-in step belongs in the importer for either.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val importRepository: PlaylistImportRepository,
    private val googleAccountManager: GoogleAccountManager,
    private val deezerAccountManager: DeezerAccountManager,
    private val parserCoordinator: PlaylistParserCoordinator
) : ViewModel() {

    constructor(
        spotifyParser: SpotifyPlaylistParser,
        deezerParser: DeezerPlaylistParser,
        youtubeParser: YouTubePlaylistParser,
        appleMusicParser: AppleMusicPlaylistParser,
        textParser: TextPlaylistParser,
        importRepository: PlaylistImportRepository,
        googleAccountManager: GoogleAccountManager,
        deezerAccountManager: DeezerAccountManager
    ) : this(
        importRepository = importRepository,
        googleAccountManager = googleAccountManager,
        deezerAccountManager = deezerAccountManager,
        parserCoordinator = PlaylistParserCoordinator(
            spotifyParser,
            deezerParser,
            youtubeParser,
            appleMusicParser,
            textParser
        )
    )

    private val _state = MutableStateFlow(PlaylistImportUiState())
    val state: StateFlow<PlaylistImportUiState> = _state.asStateFlow()

    val progress: StateFlow<ImportProgress> = importRepository.progress
    val isYouTubeLoggedIn: StateFlow<Boolean> = googleAccountManager.isLoggedIn
    val isDeezerLoggedIn: StateFlow<Boolean> = deezerAccountManager.isLoggedIn

    fun onWebUrlChanged(url: String) {
        val detected = detectPlaylistUrl(url)
        if (detected != null && (_state.value.manualInput.isBlank() || _state.value.manualInput.startsWith("http"))) {
            _state.value = _state.value.copy(manualInput = detected)
        }
    }

    private fun detectPlaylistUrl(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.contains("spotify.com/playlist/") || trimmed.contains("spotify:playlist:") || trimmed.contains("spotify.link/") -> trimmed
            trimmed.contains("deezer.com") && trimmed.contains("/playlist/") -> trimmed
            trimmed.contains("music.apple.com") && trimmed.contains("/playlist/") -> trimmed
            (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) && (trimmed.contains("list=") || trimmed.contains("/playlist")) -> trimmed
            else -> null
        }
    }

    private fun isLoggedInElsewhere(platform: PlatformType): Boolean = when (platform) {
        PlatformType.YOUTUBE -> googleAccountManager.isLoggedIn.value
        PlatformType.DEEZER -> deezerAccountManager.isLoggedIn.value
        else -> false
    }

    fun selectPlatform(platform: PlatformType) {
        _state.value = _state.value.copy(
            platform = platform,
            discoveredPlaylists = emptyList(),
            hasCheckedDiscovery = false,
            discoveryDismissed = false,
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            manualInput = "",
            error = null
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

    /** Called on returning from Settings' YouTube or Deezer sign-in — picks the account state back up. */
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
    fun checkDeezerPlaylists() = checkOwnAccountPlaylists(PlatformType.DEEZER)

    private fun checkOwnAccountPlaylists(platform: PlatformType) {
        val fetch = when (platform) {
            PlatformType.YOUTUBE -> parserCoordinator::fetchYouTubePlaylists
            PlatformType.DEEZER -> parserCoordinator::fetchDeezerPlaylists
            else -> return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true)
            fetch().onSuccess { lists ->
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
        _state.value = _state.value.copy(manualInput = input, error = null)
    }

    fun loadPlaylist(url: String, fallbackTitle: String? = null, fallbackCover: String? = null) {
        val platform = PlatformType.detect(url)
        val cookie = when (platform) {
            PlatformType.DEEZER -> android.webkit.CookieManager.getInstance().getCookie("https://www.deezer.com")
            PlatformType.APPLE_MUSIC -> android.webkit.CookieManager.getInstance().getCookie("https://music.apple.com")
            PlatformType.YOUTUBE -> android.webkit.CookieManager.getInstance().getCookie("https://music.youtube.com")
            PlatformType.SPOTIFY, PlatformType.PLAIN_TEXT -> null
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlaylist = true, error = null)
            parserCoordinator.parsePlaylist(url, fallbackTitle, fallbackCover, cookie).onSuccess { updated ->
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
