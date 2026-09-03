package com.wander.android.ui.screens.importer

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.importer.AppleMusicPlaylistParser
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawUserPlaylistSummary
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import com.wander.android.data.repository.PlaylistImportRepository
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlaylistImportUiState(
    val platform: PlatformType = PlatformType.SPOTIFY,
    val isWebMode: Boolean = true,
    val webUrl: String = PlatformType.SPOTIFY.webUrl,
    val reloadToken: Int = 0,
    val pageError: String? = null,
    val showWebBrowser: Boolean = false,
    val detectedUrl: String? = null,
    val isDiscovering: Boolean = false,
    val discoveredPlaylists: List<RawUserPlaylistSummary> = emptyList(),
    val isLoadingPlaylist: Boolean = false,
    val loadedPlaylist: RawImportPlaylist? = null,
    val selectedIndices: Set<Int> = emptySet(),
    val manualInput: String = "",
    val error: String? = null
)

@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val spotifyParser: SpotifyPlaylistParser,
    private val deezerParser: DeezerPlaylistParser,
    private val youtubeParser: YouTubePlaylistParser,
    private val appleMusicParser: AppleMusicPlaylistParser,
    private val textParser: TextPlaylistParser,
    private val importRepository: PlaylistImportRepository,
    private val googleAccountManager: GoogleAccountManager
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistImportUiState())
    val state: StateFlow<PlaylistImportUiState> = _state.asStateFlow()

    val progress: StateFlow<ImportProgress> = importRepository.progress

    init {
        checkCurrentPlatform(PlatformType.SPOTIFY)
    }

    fun selectPlatform(platform: PlatformType) {
        _state.value = _state.value.copy(
            platform = platform,
            webUrl = platform.webUrl,
            reloadToken = _state.value.reloadToken + 1,
            pageError = null,
            showWebBrowser = false,
            detectedUrl = null,
            discoveredPlaylists = emptyList(),
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            error = null
        )
        checkCurrentPlatform(platform)
    }

    fun openWebBrowser() {
        _state.value = _state.value.copy(showWebBrowser = true)
    }

    fun onPageError(message: String) {
        _state.value = _state.value.copy(pageError = message)
    }

    fun clearPageError() {
        _state.value = _state.value.copy(pageError = null)
    }

    fun closeWebBrowser() {
        _state.value = _state.value.copy(showWebBrowser = false)
    }

    fun clearLoadedPlaylist() {
        _state.value = _state.value.copy(
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            error = null
        )
    }

    fun logout() {
        val platform = _state.value.platform
        if (platform == PlatformType.SPOTIFY) {
            CookieManager.getInstance().apply {
                setCookie(SPOTIFY_ORIGIN, "sp_dc=; Max-Age=0")
                setCookie("https://accounts.spotify.com", "sp_dc=; Max-Age=0")
                flush()
            }
        }
        _state.value = _state.value.copy(
            discoveredPlaylists = emptyList(),
            showWebBrowser = true,
            webUrl = platform.webUrl,
            reloadToken = _state.value.reloadToken + 1,
            pageError = null
        )
    }

    private fun checkCurrentPlatform(platform: PlatformType) {
        when (platform) {
            PlatformType.SPOTIFY -> {
                val cookie = CookieManager.getInstance().getCookie(SPOTIFY_ORIGIN)
                    ?: CookieManager.getInstance().getCookie("https://accounts.spotify.com")
                if (!cookie.isNullOrBlank() && cookie.contains("sp_")) {
                    checkSpotifyPlaylists(cookie)
                }
            }
            PlatformType.YOUTUBE -> {
                if (googleAccountManager.isLoggedIn.value) {
                    checkYouTubePlaylists()
                }
            }
            else -> {}
        }
    }

    fun onCookieCaptured(cookie: String?) {
        if (cookie.isNullOrBlank()) return
        val platform = _state.value.platform
        if (platform == PlatformType.SPOTIFY && cookie.contains("sp_")) {
            checkSpotifyPlaylists(cookie)
        }
    }

    fun checkSpotifyPlaylists(cookie: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true)
            val result = withContext(Dispatchers.IO) {
                spotifyParser.fetchUserPlaylists(cookie)
            }
            result.onSuccess { lists ->
                if (lists.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        isDiscovering = false,
                        discoveredPlaylists = lists,
                        showWebBrowser = false,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(isDiscovering = false)
                }
            }.onFailure { err ->
                // Not being signed in yet is the expected state while the browser is open,
                // so only a real failure is worth putting in front of the user.
                val message = err.message?.takeUnless { it.startsWith(SIGN_IN_PROMPT) }
                _state.value = _state.value.copy(isDiscovering = false, error = message)
            }
        }
    }

    fun checkYouTubePlaylists() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true)
            val result = withContext(Dispatchers.IO) {
                youtubeParser.fetchUserPlaylists()
            }
            result.onSuccess { lists ->
                if (lists.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        isDiscovering = false,
                        discoveredPlaylists = lists,
                        showWebBrowser = false,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(isDiscovering = false)
                }
            }.onFailure {
                _state.value = _state.value.copy(isDiscovering = false)
            }
        }
    }

    fun setWebMode(isWeb: Boolean) {
        _state.value = _state.value.copy(isWebMode = isWeb, error = null)
    }

    fun setManualInput(input: String) {
        val detected = PlatformType.detect(input)
        _state.value = _state.value.copy(manualInput = input, platform = detected, error = null)
    }

    fun onWebPageUrlChanged(url: String) {
        val detected = detectPlaylistUrl(url)
        _state.value = _state.value.copy(detectedUrl = detected)
    }

    private fun detectPlaylistUrl(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.contains("spotify.com/playlist/") || trimmed.contains("spotify.link/") -> trimmed
            trimmed.contains("deezer.com") && trimmed.contains("/playlist/") -> trimmed
            trimmed.contains("youtube.com") && trimmed.contains("list=") -> trimmed
            trimmed.contains("music.apple.com") && trimmed.contains("/playlist/") -> trimmed
            else -> null
        }
    }

    fun loadPlaylist(url: String, fallbackTitle: String? = null, fallbackCover: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlaylist = true, error = null)
            val platform = PlatformType.detect(url)
            val cookie = if (platform == PlatformType.SPOTIFY) {
                CookieManager.getInstance().getCookie(SPOTIFY_ORIGIN)
            } else null

            val result: Result<RawImportPlaylist> = withContext(Dispatchers.IO) {
                when (platform) {
                    PlatformType.SPOTIFY -> spotifyParser.parse(url, cookie)
                    PlatformType.DEEZER -> deezerParser.parse(url)
                    PlatformType.YOUTUBE -> youtubeParser.parse(url)
                    PlatformType.APPLE_MUSIC -> appleMusicParser.parse(url)
                    PlatformType.PLAIN_TEXT -> textParser.parse(url)
                }
            }

            result.onSuccess { playlist ->
                val finalTitle = if (!fallbackTitle.isNullOrBlank() && (playlist.title.contains("Playlist", ignoreCase = true) || playlist.title.isBlank())) {
                    fallbackTitle
                } else {
                    playlist.title
                }
                val finalCover = playlist.coverUrl ?: fallbackCover
                val updated = playlist.copy(title = finalTitle, coverUrl = finalCover)
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

    private companion object {
        /** Cookie-jar origin for the Spotify web session; not a request URL. */
        const val SPOTIFY_ORIGIN = "https://open.spotify.com"
        const val SIGN_IN_PROMPT = "Please sign in"
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
