package com.wander.android.ui.screens.importer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.importer.ImportProgress
import com.wander.android.data.importer.PlatformType

private enum class ImporterStage {
    MAIN,
    CHECKLIST,
    PROGRESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    viewModel: PlaylistImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Shared-axis motion, from the theme rather than hand-picked durations.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val hasInternalBackState = (progress !is ImportProgress.Idle) ||
        (state.loadedPlaylist != null) ||
        (state.showWebBrowser && state.discoveredPlaylists.isNotEmpty())

    BackHandler(enabled = hasInternalBackState) {
        when {
            progress is ImportProgress.Success || progress is ImportProgress.Failed -> viewModel.reset()
            state.loadedPlaylist != null -> viewModel.clearLoadedPlaylist()
            state.showWebBrowser && state.discoveredPlaylists.isNotEmpty() -> viewModel.closeWebBrowser()
        }
    }

    val handleBarBack: () -> Unit = {
        if (hasInternalBackState) {
            when {
                progress is ImportProgress.Success || progress is ImportProgress.Failed -> viewModel.reset()
                state.loadedPlaylist != null -> viewModel.clearLoadedPlaylist()
                state.showWebBrowser && state.discoveredPlaylists.isNotEmpty() -> viewModel.closeWebBrowser()
            }
        } else {
            onBack()
        }
    }

    val currentStage = when {
        progress !is ImportProgress.Idle -> ImporterStage.PROGRESS
        state.loadedPlaylist != null -> ImporterStage.CHECKLIST
        else -> ImporterStage.MAIN
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Playlist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBarBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentStage,
                transitionSpec = {
                    if (targetState == ImporterStage.CHECKLIST) {
                        (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { -it / 3 } + fadeOut(effects))
                    } else if (initialState == ImporterStage.CHECKLIST) {
                        (slideInHorizontally(spatial) { -it / 3 } + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
                    } else {
                        fadeIn(effects) togetherWith fadeOut(effects)
                    }
                },
                label = "importerStage"
            ) { stage ->
                when (stage) {
                    ImporterStage.PROGRESS -> {
                        ImportProgressStage(
                            progress = progress,
                            onOpenPlaylist = onOpenPlaylist,
                            onReset = viewModel::reset
                        )
                    }

                    ImporterStage.CHECKLIST -> {
                        state.loadedPlaylist?.let { playlist ->
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
                    }

                    ImporterStage.MAIN -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PrimaryTabRow(selectedTabIndex = if (state.isWebMode) 0 else 1) {
                                Tab(
                                    selected = state.isWebMode,
                                    onClick = { viewModel.setWebMode(true) },
                                    text = { Text("Connect & Auto-detect", fontWeight = FontWeight.SemiBold) }
                                )
                                Tab(
                                    selected = !state.isWebMode,
                                    onClick = { viewModel.setWebMode(false) },
                                    text = { Text("Direct Link / Text", fontWeight = FontWeight.SemiBold) }
                                )
                            }

                            AnimatedContent(
                                targetState = state.isWebMode,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally(spatial) { -it / 3 } + fadeIn(effects)) togetherWith
                                            (slideOutHorizontally(spatial) { it / 3 } + fadeOut(effects))
                                    } else {
                                        (slideInHorizontally(spatial) { it / 3 } + fadeIn(effects)) togetherWith
                                            (slideOutHorizontally(spatial) { -it / 3 } + fadeOut(effects))
                                    }
                                },
                                label = "importerTabContent",
                                modifier = Modifier.fillMaxSize()
                            ) { isWeb ->
                                if (isWeb) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            items(
                                                listOf(
                                                    PlatformType.SPOTIFY,
                                                    PlatformType.DEEZER,
                                                    PlatformType.YOUTUBE,
                                                    PlatformType.APPLE_MUSIC
                                                )
                                            ) { platform ->
                                                val isSelected = state.platform == platform
                                                ToggleButton(
                                                    checked = isSelected,
                                                    onCheckedChange = { viewModel.selectPlatform(platform) },
                                                    content = {
                                                        PlatformIcon(
                                                            platform = platform,
                                                            size = 18.dp,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = platform.displayName,
                                                            modifier = Modifier.padding(start = 8.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        if (state.discoveredPlaylists.isNotEmpty() && !state.showWebBrowser) {
                                            DiscoveredPlaylistsGrid(
                                                platform = state.platform,
                                                playlists = state.discoveredPlaylists,
                                                onSelectPlaylist = { viewModel.loadPlaylist(it.url, it.name, it.coverUrl) },
                                                onRelog = viewModel::openWebBrowser,
                                                onLogout = viewModel::logout,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            ImportWebView(
                                                webUrl = state.webUrl,
                                                reloadToken = state.reloadToken,
                                                detectedUrl = state.detectedUrl,
                                                isLoadingPlaylist = state.isLoadingPlaylist,
                                                isDiscovering = state.isDiscovering,
                                                pageError = state.pageError,
                                                onUrlChanged = viewModel::onWebPageUrlChanged,
                                                onCookieCaptured = viewModel::onCookieCaptured,
                                                onPageError = viewModel::onPageError,
                                                onClearPageError = viewModel::clearPageError,
                                                onLoadPlaylist = viewModel::loadPlaylist,
                                                onLogout = viewModel::logout,
                                                modifier = Modifier.fillMaxWidth().weight(1f)
                                            )
                                        }
                                    }
                                } else {
                                    ImportDirectLinkContent(
                                        manualInput = state.manualInput,
                                        isLoadingPlaylist = state.isLoadingPlaylist,
                                        error = state.error,
                                        onInputChange = viewModel::setManualInput,
                                        onLoadPlaylist = { viewModel.loadPlaylist(state.manualInput) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoadingPlaylist && state.loadedPlaylist == null && progress is ImportProgress.Idle) {
                ImportLoadingOverlay()
            }
        }
    }
}
