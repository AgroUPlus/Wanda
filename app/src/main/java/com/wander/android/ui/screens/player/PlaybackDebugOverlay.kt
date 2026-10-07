package com.wander.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wander.android.R
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
internal fun PlaybackDebugOverlay(track: UnifiedTrack, debug: StreamDebug?) {
    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                .padding(8.dp)
        ) {
            val style = MaterialTheme.typography.labelSmall
            val color = MaterialTheme.colorScheme.inverseOnSurface
            val lines = buildList {
                add(stringResource(R.string.debug_track, track.source.name, track.id))
                if (track.isLive) add(stringResource(R.string.debug_live))
                if (debug == null) {
                    add(stringResource(R.string.debug_unresolved))
                } else {
                    add(stringResource(R.string.debug_route, stringResource(debug.route.label())))
                    debug.client?.let { add(stringResource(R.string.debug_client, it)) }
                    debug.host?.let { add(stringResource(R.string.debug_host, it)) }
                    add(stringResource(R.string.debug_format, debug.format, debug.bitRateKbps))
                }
            }
            lines.forEach { Text(it, style = style, color = color) }
        }
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
