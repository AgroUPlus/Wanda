package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wander.android.R
import com.wander.android.core.playback.LoudnessDecision
import com.wander.android.core.playback.StreamDebug
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.StreamRoute

/**
 * Where the playing track really comes from, drawn over the player while debug mode is on.
 *
 * A non-focusable [Popup] so it floats over the layouts without taking part in their measuring or
 * swallowing a touch outside its own bounds.
 */
@Composable
internal fun PlaybackDebugOverlay(track: UnifiedTrack, info: PlaybackDebugInfo?, playerDurationMs: Long) {
    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(8.dp)
        ) {
            // Fixed colours, not theme ones: a theme's "inverse" text turned grey-on-black in dark mode.
            val style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            val color = Color.White
            val lines = buildList {
                add(stringResource(R.string.debug_track, track.source.name, track.id))
                if (track.isLive) add(stringResource(R.string.debug_live))
                add(stringResource(R.string.debug_duration, playerDurationMs, track.durationMs))
                info?.loudness?.let { add(loudnessLine(it)) }
                val debug = info?.stream
                if (debug == null) {
                    add(stringResource(R.string.debug_unresolved))
                } else {
                    debug.error?.let { add(stringResource(R.string.debug_error, it)) }
                    debug.route?.let { add(stringResource(R.string.debug_route, stringResource(it.label()))) }
                    debug.client?.let { add(stringResource(R.string.debug_client, it)) }
                    debug.host?.let { add(stringResource(R.string.debug_host, it)) }
                    debug.format?.let { add(stringResource(R.string.debug_format, it, debug.bitRateKbps)) }
                    debug.note?.let { add(stringResource(R.string.debug_note, it)) }
                }
            }
            lines.forEach { Text(it, style = style, color = color) }
        }
    }
}

@Composable
private fun loudnessLine(decision: LoudnessDecision): String {
    val gainDb = decision.gainDb
    return when {
        decision.isOff -> stringResource(R.string.debug_gain_off)
        gainDb == null -> stringResource(R.string.debug_gain_none, decision.factor)
        else -> stringResource(
            R.string.debug_gain,
            gainDb,
            stringResource(if (decision.fromAlbum) R.string.debug_gain_album else R.string.debug_gain_track),
            decision.factor
        )
    }
}

private fun StreamRoute.label(): Int = when (this) {
    StreamRoute.DOWNLOAD -> R.string.debug_route_download
    StreamRoute.LOCAL_COPY -> R.string.debug_route_local_copy
    StreamRoute.NAVIDROME -> R.string.debug_route_navidrome
    StreamRoute.SOURCE -> R.string.debug_route_source
    StreamRoute.EPHEMERAL -> R.string.debug_route_ephemeral
    StreamRoute.PODCAST -> R.string.debug_route_podcast
}
