package com.wander.android.ui.screens.importer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.importer.ImportProgress

/**
 * Step 1 picks a platform, step 2 gets a playlist (a share link for every platform; YouTube alone
 * also offers browsing the account this app already has), step 3 picks tracks, step 4 imports.
 *
 * For external platforms (Spotify, Deezer, Apple Music), an embedded WebView catches cookies while
 * manual link input allows importing any shareable playlist link.
 */
private enum class ImporterStage(val step: Int, val labelRes: Int) {
    PLATFORM(1, R.string.importer_step_platform_label),
    ACCESS(2, R.string.importer_step_connect_label),
    CHECKLIST(3, R.string.importer_step_tracks_label),
    PROGRESS(4, R.string.importer_step_import_label)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    onOpenDeezerLogin: () -> Unit = {},
    viewModel: PlaylistImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isYouTubeLoggedIn by viewModel.isYouTubeLoggedIn.collectAsStateWithLifecycle()
    val isDeezerLoggedIn by viewModel.isDeezerLoggedIn.collectAsStateWithLifecycle()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckSessions()
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
                            isDeezerLoggedIn = isDeezerLoggedIn,
                            actions = AccessStepActions(
                                onSelectPlaylist = { viewModel.loadPlaylist(it.url, it.name, it.coverUrl) },
                                onRefreshYouTube = viewModel::checkYouTubePlaylists,
                                onRefreshDeezer = viewModel::checkDeezerPlaylists,
                                onSwitchToDirectLink = viewModel::switchToDirectLink,
                                onOpenYouTubeLogin = onOpenYouTubeLogin,
                                onOpenDeezerLogin = onOpenDeezerLogin,
                                onExternalWebViewReady = viewModel::setExternalWebView,
                                onWebUrlChanged = viewModel::onWebUrlChanged,
                                onInputChange = viewModel::setManualInput,
                                onLoadPlaylist = { viewModel.loadPlaylist(state.manualInput) }
                            ),
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
