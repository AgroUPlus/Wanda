package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * A miniature of the real full-screen lyrics dialog (`FullScreenLyrics.kt`): a title and three
 * mock lines, the middle one tinted as the currently-playing line. Fades over the whole frame —
 * real lyrics replace the *entire* player, not just the cover — as [alpha] rises from the tap.
 */
@Composable
internal fun LyricsOverlay(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHighest)
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.player_lyrics),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        LyricLine(stringResource(R.string.welcome_gesture_mock_lyric_line_1), active = false, scheme = scheme)
        LyricLine(stringResource(R.string.welcome_gesture_mock_lyric_line_2), active = true, scheme = scheme)
        LyricLine(stringResource(R.string.welcome_gesture_mock_lyric_line_3), active = false, scheme = scheme)
    }
}

@Composable
private fun LyricLine(text: String, active: Boolean, scheme: ColorScheme) {
    Text(
        text = text,
        style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
        color = if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}
