package com.wander.android.ui.screens.importer

import android.webkit.WebView
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
 * Apple Music's parser, and Spotify's (via the web player's anonymous token, see
 * [SpotifyPlaylistParser.parse]), read a playlist by its share link with no session at all — that
 * covers the playlists someone would actually paste in here, but not a playlist that was never
 * shared, or Liked Songs, which have no link at all; those need Spotify's real OAuth, deliberately
 * left out for now (it needs a Developer Dashboard app of the user's own to add).
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

    /**
     * The live WebView backing the external platform's browse step, once it exists — see
     * [ExternalPlatformWebView] and [SpotifyWebFetch].
     * A plain field, not state: it is Android View plumbing the screen owns, not UI state to render.
     */
    private var externalWebView: WebView? = null

    fun setExternalWebView(webView: WebView?) {
        externalWebView = webView
    }

    fun setSpotifyWebView(webView: WebView?) = setExternalWebView(webView)

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
        val fetcher: (suspend (String, Map<String, String>) -> String)? = externalWebView?.let { webView ->
            { fetchUrl: String, headers: Map<String, String> -> webView.fetchText(fetchUrl, headers) }
        }
        val platform = PlatformType.detect(url)
        val cookie = when (platform) {
            PlatformType.SPOTIFY -> android.webkit.CookieManager.getInstance().getCookie("https://open.spotify.com")
                ?: android.webkit.CookieManager.getInstance().getCookie("https://accounts.spotify.com")
            PlatformType.DEEZER -> android.webkit.CookieManager.getInstance().getCookie("https://www.deezer.com")
            PlatformType.APPLE_MUSIC -> android.webkit.CookieManager.getInstance().getCookie("https://music.apple.com")
            PlatformType.YOUTUBE -> android.webkit.CookieManager.getInstance().getCookie("https://music.youtube.com")
            PlatformType.PLAIN_TEXT -> null
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlaylist = true, error = null)
            parserCoordinator.parsePlaylist(url, fallbackTitle, fallbackCover, fetcher, cookie).onSuccess { updated ->
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
