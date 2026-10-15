package com.wander.android.ui.screens.player

import com.wander.android.core.playback.LoudnessDecision
import com.wander.android.core.playback.StreamDebug

/**
 * What the debug overlay knows about the playing track. [stream] is null until it is resolved —
 * and always for a local file, which is never resolved; [loudness] is null until its gain is decided.
 */
internal data class PlaybackDebugInfo(val stream: StreamDebug?, val loudness: LoudnessDecision?)
