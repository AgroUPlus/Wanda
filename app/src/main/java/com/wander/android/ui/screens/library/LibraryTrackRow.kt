package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow

/**
 * A Library track row: the shared [TrackRow] with a ⋮ at its end in place of the heart.
 *
 * The ⋮ opens the same sheet a long press does, which is where Like lives too: a row in a list of
 * hundreds is for finding and playing, and a press that is not on the cover or the title should
 * not change the library under the finger.
 */
@Composable
internal fun LibraryTrackRow(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onLongPress: (() -> Unit)?,
    onMore: () -> Unit,
    selected: Boolean?,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        TrackRow(
            track = track,
            onPlay = onPlay,
            onLongPress = onLongPress,
            selected = selected,
            modifier = Modifier.weight(1f)
        )
        // Mid-selection a second target on the row would do something other than select it.
        if (selected == null) LibraryMoreButton(onMore, Modifier.padding(end = 4.dp))
    }
}
