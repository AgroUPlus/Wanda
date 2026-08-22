package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline

/**
 * Whether playback is currently cut off from the network — offline mode is on, or there is no
 * connection.
 *
 * An ambient rather than a field on six separate `UiState`s. Every list in the app renders the
 * same shared rows, so "can this be played right now" is chrome that applies to all of them at
 * once, the way content colour does; threading an identical boolean through Home, Library, Search,
 * Album, Artist and Queue would have each screen restating a fact none of them own.
 *
 * Defaults to false so a preview or a test that does not provide it renders the normal, enabled
 * UI rather than a screen that looks broken.
 *
 * Deliberately consumed by tracks only. Albums and playlists have no per-item download state to
 * consult, so dimming them would be a guess — a Navidrome album whose tracks are all downloaded
 * plays perfectly offline. Browsing stays open and the rows inside tell the truth individually.
 */
val LocalOfflinePlayback = compositionLocalOf { false }

/** True when this track can be played given the current network state. */
@Composable
@ReadOnlyComposable
fun UnifiedTrack.isPlayableNow(): Boolean =
    !LocalOfflinePlayback.current || isPlayableOffline()
