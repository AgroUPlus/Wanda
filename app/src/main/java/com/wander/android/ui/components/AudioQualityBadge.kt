package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.core.playback.ActualAudioFormat
import java.util.Locale

/**
 * A concise badge displaying audio format and bitrate (e.g. FLAC, 320 kbps, Lossless).
 *
 * [actualFormat], when present, overrides [quality] with the real decoded format — "96 kHz ·
 * 24-bit · FLAC" — rather than the guess from container tags [quality] is normally given. See
 * [ActualAudioFormat] for why the two can disagree.
 */
@Composable
fun AudioQualityBadge(
    quality: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actualFormat: ActualAudioFormat? = null
) {
    val label = actualFormat?.let(::describe) ?: quality.uppercase()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.6.sp
            ),
            color = contentColor,
            maxLines = 1
        )
    }
}

private fun describe(format: ActualAudioFormat): String {
    val parts = buildList {
        if (format.sampleRateHz > 0) add("${format.sampleRateHz / 1000} kHz")
        format.bitDepth?.let { add("$it-bit") }
        val codec = format.mimeType?.substringAfterLast('/')?.uppercase(Locale.US)
        when {
            codec != null -> add(codec)
            format.bitrateKbps != null -> add("${format.bitrateKbps} kbps")
        }
    }
    return parts.joinToString(" · ")
}
