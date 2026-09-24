package com.wander.android.ui.screens.importer

import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawUserPlaylistSummary

data class PlaylistImportUiState(
    /** Null means step 1 — no platform chosen yet. */
    val platform: PlatformType? = null,
    val isDiscovering: Boolean = false,
    /** Only ever populated for YouTube, the one platform this app already has an account for. */
    val discoveredPlaylists: List<RawUserPlaylistSummary> = emptyList(),
    val isLoadingPlaylist: Boolean = false,
    val loadedPlaylist: RawImportPlaylist? = null,
    val selectedIndices: Set<Int> = emptySet(),
    val manualInput: String = "",
    val error: String? = null
)
