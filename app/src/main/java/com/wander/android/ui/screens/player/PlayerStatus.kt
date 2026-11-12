package com.wander.android.ui.screens.player

import com.wander.android.core.playback.SleepTimerState
import com.wander.android.data.sources.agro.Jam

/**
 * What the player says about the session rather than the track: a live jam, the song/video toggle
 * for a track that has both forms, and a running sleep timer. Each of these shows only while it
 * applies, which is why they ride the cover between its two buttons instead of taking a row.
 */
internal class PlayerStatus(
    val jam: Jam?,
    val mediaToggle: MediaToggleState?,
    val onSwapMediaType: () -> Unit,
    val sleepTimer: SleepTimerState,
    val onOpenJam: () -> Unit
)
