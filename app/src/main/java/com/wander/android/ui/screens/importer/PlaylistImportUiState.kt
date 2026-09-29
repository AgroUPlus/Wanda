package com.wander.android.ui.screens.importer

import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawUserPlaylistSummary

data class PlaylistImportUiState(
    /** Null means step 1 — no platform chosen yet. */
    val platform: PlatformType? = null,
    val isDiscovering: Boolean = false,
    /** Only ever populated for YouTube and Deezer, the two platforms this app already has an account for. */
    val discoveredPlaylists: List<RawUserPlaylistSummary> = emptyList(),
    /**
     * Set once a discovery fetch finishes, success or not — distinguishes "haven't looked yet" from
     * "looked and found nothing" so an empty [discoveredPlaylists] doesn't silently read as the
     * paste-a-link screen nobody asked for.
     */
    val hasCheckedDiscovery: Boolean = false,
    /** True once the person explicitly picks "paste a link instead" over a non-empty grid. */
    val discoveryDismissed: Boolean = false,
    val isLoadingPlaylist: Boolean = false,
    val loadedPlaylist: RawImportPlaylist? = null,
    val selectedIndices: Set<Int> = emptySet(),
    val manualInput: String = "",
    val error: String? = null
)
