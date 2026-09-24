package com.wander.android.ui.screens.importer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawUserPlaylistSummary

/**
 * Step 1 picks a platform, step 2 gets a playlist (a share link for every platform; YouTube alone
 * also offers browsing the account this app already has), step 3 picks tracks, step 4 imports.
 *
 * Deezer, Apple Music and YouTube all read a playlist from a plain HTTP call with no session at
 * all. Spotify's *should* too — the web player exposes an anonymous token — but Spotify's own
 * endpoint now rejects that exact call from any client that isn't a real browser, cookie or no
 * cookie, with a `400` and a note about its Developer Terms. So Spotify alone shows a real, live
 * `open.spotify.com` in a WebView: partly so the user can see and find a playlist visually, and
 * partly because that WebView's own `fetch()` is the only thing here Spotify still answers (see
 * [SpotifyPlaylistParser]'s class doc and [SpotifyWebFetch]).
 */
private enum class ImporterStage(val step: Int, val labelRes: Int) {
    PLATFORM(1, R.string.importer_step_platform_label),
    ACCESS(2, R.string.importer_step_connect_label),
    CHECKLIST(3, R.string.importer_step_tracks_label),
    PROGRESS(4, R.string.importer_step_import_label)
}

private val PICKER_PLATFORMS = listOf(
    PlatformType.SPOTIFY,
    PlatformType.DEEZER,
    PlatformType.YOUTUBE,
    PlatformType.APPLE_MUSIC,
    PlatformType.PLAIN_TEXT
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    viewModel: PlaylistImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Picks a YouTube sign-in back up on return from Settings — the only remaining "went
    // somewhere else to sign in" path now that Spotify needs no sign-in step at all.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckYouTubeSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentStage = when {
        progress !is ImportProgress.Idle -> ImporterStage.PROGRESS
        state.loadedPlaylist != null -> ImporterStage.CHECKLIST
        state.platform == null -> ImporterStage.PLATFORM
        else -> ImporterStage.ACCESS
    }

    val handleBack: () -> Unit = {
        when {
            progress is ImportProgress.Success || progress is ImportProgress.Failed -> viewModel.reset()
            state.loadedPlaylist != null -> viewModel.clearLoadedPlaylist()
            state.platform != null -> viewModel.backToPlatformPicker()
            else -> onBack()
        }
    }

    BackHandler(enabled = currentStage != ImporterStage.PLATFORM, onBack = handleBack)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.importer_import_playlist), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                )
                Text(
                    text = stringResource(
                        R.string.importer_step_x_of_y,
                        currentStage.step,
                        ImporterStage.entries.size
                    ) + " · " + stringResource(currentStage.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = currentStage,
                transitionSpec = {
                    if (targetState.step >= initialState.step) {
                        (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { -it / 3 } + fadeOut(effects))
                    } else {
                        (slideInHorizontally(spatial) { -it / 3 } + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
                    }
                },
                label = "importerStage"
            ) { stage ->
                when (stage) {
                    ImporterStage.PROGRESS -> ImportProgressStage(
                        progress = progress,
                        onOpenPlaylist = onOpenPlaylist,
                        onReset = viewModel::reset
                    )

                    ImporterStage.CHECKLIST -> state.loadedPlaylist?.let { playlist ->
                        ImportTrackSelector(
                            playlist = playlist,
                            selectedIndices = state.selectedIndices,
                            onToggleTrack = viewModel::toggleTrack,
                            onSelectAll = viewModel::selectAll,
                            onDeselectAll = viewModel::deselectAll,
                            onConfirmImport = { customTitle -> viewModel.startImport(customTitle) },
                            onCancel = viewModel::clearLoadedPlaylist,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ImporterStage.PLATFORM -> PlatformPickerStep(
                        onSelect = viewModel::selectPlatform,
                        modifier = Modifier.fillMaxSize()
                    )

                    ImporterStage.ACCESS -> state.platform?.let { platform ->
                        AccessStep(
                            platform = platform,
                            state = state,
                            isYouTubeLoggedIn = isYouTubeLoggedIn,
                            onSelectPlaylist = { viewModel.loadPlaylist(it.url, it.name, it.coverUrl) },
                            onRefreshYouTube = viewModel::checkYouTubePlaylists,
                            onSwitchToDirectLink = viewModel::switchToDirectLink,
                            onOpenYouTubeLogin = onOpenYouTubeLogin,
                            onSpotifyWebViewReady = viewModel::setSpotifyWebView,
                            onInputChange = viewModel::setManualInput,
                            onLoadPlaylist = { viewModel.loadPlaylist(state.manualInput) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (state.isLoadingPlaylist && state.loadedPlaylist == null && progress is ImportProgress.Idle) {
                ImportLoadingOverlay()
            }
        }
    }
}

/** Step 1: a grid of big, tappable platform cards — the only choice this step asks for. */
@Composable
private fun PlatformPickerStep(onSelect: (PlatformType) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(PICKER_PLATFORMS, key = { it.name }) { platform ->
            PlatformCard(platform = platform, onClick = { onSelect(platform) })
        }
    }
}

@Composable
private fun PlatformCard(platform: PlatformType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "platformCardScale"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 32.dp else 20.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "platformCardMorph"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .scale(scale)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            PlatformIcon(platform = platform, size = 40.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/** Step 2: how to get a playlist, once a platform is chosen. */
@Composable
private fun AccessStep(
    platform: PlatformType,
    state: PlaylistImportUiState,
    isYouTubeLoggedIn: Boolean,
    onSelectPlaylist: (RawUserPlaylistSummary) -> Unit,
    onRefreshYouTube: () -> Unit,
    onSwitchToDirectLink: () -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onSpotifyWebViewReady: (android.webkit.WebView) -> Unit,
    onInputChange: (String) -> Unit,
    onLoadPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        platform == PlatformType.SPOTIFY -> Column(modifier = modifier) {
            SpotifyBrowseWebView(
                onWebViewReady = onSpotifyWebViewReady,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            ImportDirectLinkContent(
                manualInput = state.manualInput,
                isLoadingPlaylist = state.isLoadingPlaylist,
                error = state.error,
                onInputChange = onInputChange,
                onLoadPlaylist = onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.discoveredPlaylists.isNotEmpty() -> DiscoveredPlaylistsGrid(
            platform = platform,
            playlists = state.discoveredPlaylists,
            onSelectPlaylist = onSelectPlaylist,
            onRefresh = onRefreshYouTube,
            onPasteLinkInstead = onSwitchToDirectLink,
            modifier = modifier
        )

        platform == PlatformType.YOUTUBE && state.isDiscovering -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }

        platform == PlatformType.YOUTUBE && !isYouTubeLoggedIn -> Column(modifier = modifier.padding(16.dp)) {
            YouTubeSignInPrompt(onOpenYouTubeLogin = onOpenYouTubeLogin)
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            Text(
                text = stringResource(R.string.importer_or_paste_link),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ImportDirectLinkContent(
                manualInput = state.manualInput,
                isLoadingPlaylist = state.isLoadingPlaylist,
                error = state.error,
                onInputChange = onInputChange,
                onLoadPlaylist = onLoadPlaylist,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> ImportDirectLinkContent(
            manualInput = state.manualInput,
            isLoadingPlaylist = state.isLoadingPlaylist,
            error = state.error,
            onInputChange = onInputChange,
            onLoadPlaylist = onLoadPlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun YouTubeSignInPrompt(onOpenYouTubeLogin: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.importer_youtube_signed_out_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.importer_youtube_signed_out_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Button(onClick = onOpenYouTubeLogin, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.importer_sign_in))
            }
        }
    }
}
