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
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Every source's parser reads a playlist by its share link with no session at all — Deezer and
 * Apple Music always did this; Spotify's does too, via the web player's anonymous token (see
 * [SpotifyPlaylistParser.parse]). A share link is exactly what "share this playlist" hands out, so
 * this covers the playlists someone would actually paste in here. It does not cover a playlist that
 * was never shared, or Liked Songs, which have no link at all — those need Spotify's real OAuth,
 * deliberately left out for now (it needs a Developer Dashboard app of the user's own to add).
 *
 * YouTube is the one platform this app already holds an account for (see [GoogleAccountManager],
 * signed in from Settings), so it alone gets a "browse my library" option — no separate sign-in
 * step belongs in the importer for it.
 */
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    private val importRepository: PlaylistImportRepository,
    private val googleAccountManager: GoogleAccountManager,
    private val parserCoordinator: PlaylistParserCoordinator
) : ViewModel() {

    constructor(
        spotifyParser: SpotifyPlaylistParser,
        deezerParser: DeezerPlaylistParser,
        youtubeParser: YouTubePlaylistParser,
        appleMusicParser: AppleMusicPlaylistParser,
        textParser: TextPlaylistParser,
        importRepository: PlaylistImportRepository,
        googleAccountManager: GoogleAccountManager
    ) : this(
        importRepository = importRepository,
        googleAccountManager = googleAccountManager,
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

    /**
     * The live WebView backing Spotify's browse step, once it exists — see [SpotifyBrowseWebView]
     * and [SpotifyPlaylistParser]'s class doc for why Spotify alone needs one just to fetch a link.
     * A plain field, not state: it is Android View plumbing the screen owns, not UI state to render.
     */
    private var spotifyWebView: WebView? = null

    fun setSpotifyWebView(webView: WebView?) {
        spotifyWebView = webView
    }

    fun selectPlatform(platform: PlatformType) {
        _state.value = _state.value.copy(
            platform = platform,
            discoveredPlaylists = emptyList(),
            loadedPlaylist = null,
            selectedIndices = emptySet(),
            manualInput = "",
            error = null
        )
        if (platform == PlatformType.YOUTUBE && googleAccountManager.isLoggedIn.value) {
            checkYouTubePlaylists()
        }
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
    fun recheckYouTubeSession() {
        if (_state.value.platform == PlatformType.YOUTUBE &&
            _state.value.discoveredPlaylists.isEmpty() &&
            googleAccountManager.isLoggedIn.value
        ) {
            checkYouTubePlaylists()
        }
    }

    /** Drops the discovered library grid so the direct-link form shows instead. */
    fun switchToDirectLink() {
        _state.value = _state.value.copy(discoveredPlaylists = emptyList())
    }

    fun checkYouTubePlaylists() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDiscovering = true)
            parserCoordinator.fetchYouTubePlaylists().onSuccess { lists ->
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    discoveredPlaylists = lists,
                    error = null
                )
            }.onFailure {
                _state.value = _state.value.copy(isDiscovering = false)
            }
        }
    }

    fun setManualInput(input: String) {
        _state.value = _state.value.copy(manualInput = input, error = null)
    }

    fun loadPlaylist(url: String, fallbackTitle: String? = null, fallbackCover: String? = null) {
        val fetcher: (suspend (String, Map<String, String>) -> String)? = spotifyWebView?.let { webView ->
            { fetchUrl: String, headers: Map<String, String> -> webView.fetchText(fetchUrl, headers) }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlaylist = true, error = null)
            parserCoordinator.parsePlaylist(url, fallbackTitle, fallbackCover, fetcher).onSuccess { updated ->
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
