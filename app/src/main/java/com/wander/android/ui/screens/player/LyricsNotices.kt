package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.LyricsState
import com.wander.android.data.model.LyricsSyncType

/**
 * Says, in words, how closely these lyrics follow the song.
 *
 * The app already knew this and only ever whispered it: the lyrics button's *icon* changes shape
 * with the sync type, and the difference between three glyphs is not something anyone reads. The
 * actual words lived in a `contentDescription`, which only a screen reader ever speaks — and in the
 * immersive layout that button is not drawn at all, so there was nothing to look at either way.
 *
 * Here instead, at the head of the thing it describes, where it is read once and answers the
 * question it is about: is this going to follow along with me, and how finely.
 */
@Composable
internal fun SyncTypeLabel(syncType: LyricsSyncType) {
    val label = when (syncType) {
        LyricsSyncType.WORD_SYNCED -> stringResource(R.string.lyrics_synced_word)
        else -> null
    } ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

/**
 * What to say when there is nothing to show.
 *
 * Each variant gets its own sentence. Telling someone in a tunnel that their song has no lyrics
 * sends them looking for a fault in the track instead of waiting for signal.
 */
@Composable
internal fun LyricsState.describe(): String = when (this) {
    LyricsState.Loading -> stringResource(R.string.lyrics_loading)
    LyricsState.Absent -> stringResource(R.string.lyrics_absent)
    LyricsState.Instrumental -> stringResource(R.string.lyrics_instrumental)
    LyricsState.Unreachable -> stringResource(R.string.lyrics_unreachable)
    is LyricsState.Present -> ""
}

@Composable
internal fun CenteredNotice(message: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
