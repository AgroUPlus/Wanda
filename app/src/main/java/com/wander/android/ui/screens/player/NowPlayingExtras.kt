package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.audio.visualizer.VisualizerMode
import com.wander.android.ui.screens.player.visualizers.WanderVisualizerHost
import kotlinx.coroutines.flow.StateFlow

/**
 * The optional visualizer and its mode picker. Lyrics live on the artwork instead — see
 * [NowPlayingScreen].
 *
 * A visualizer needs decoded PCM, which audio offload skips — turning one on therefore turns
 * offload off. That is why it defaults to [VisualizerMode.OFF]: the quiet, battery-friendly
 * choice, opted into rather than out of.
 */
@Composable
fun NowPlayingExtras(
    visualizerMode: VisualizerMode,
    onSelectVisualizer: (VisualizerMode) -> Unit,
    spectrum: StateFlow<FloatArray>,
    waveform: StateFlow<FloatArray>,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VisualizerMode.entries, key = { it.name }) { mode ->
                FilterChip(
                    selected = mode == visualizerMode,
                    onClick = { onSelectVisualizer(mode) },
                    label = { Text(mode.displayName) }
                )
            }
        }

        if (visualizerMode != VisualizerMode.OFF) {
            val bands by spectrum.collectAsStateWithLifecycle()
            val wave by waveform.collectAsStateWithLifecycle()
            WanderVisualizerHost(
                mode = visualizerMode,
                spectrum = bands,
                waveform = wave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }
    }
}
