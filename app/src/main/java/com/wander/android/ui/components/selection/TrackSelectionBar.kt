package com.wander.android.ui.components.selection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.CompactHeroBarHeight

/** How many actions sit in the bar itself; the rest are behind its overflow. */
private const val InlineActions = 3

/**
 * The contextual top bar of a list in selection mode: close, how many are selected, and what can
 * be done with them. Drawn over the screen's own header while a selection runs, and gone with it.
 *
 * Back leaves selection mode before it leaves the screen. Every action ends the selection, since
 * it has done what the selection was for.
 *
 * [topInset] is the status bar the bar must clear, for screens that draw edge to edge beneath it.
 */
@Composable
fun TrackSelectionBar(
    selection: TrackSelectionState,
    addToPlaylist: AddToPlaylistController?,
    topInset: Dp,
    modifier: Modifier = Modifier,
    extraActions: List<SelectionAction> = emptyList()
) {
    BackHandler(enabled = selection.isActive) { selection.clear() }
    AnimatedVisibility(
        visible = selection.isActive,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            val actions = rememberSelectionActions(selection, addToPlaylist, extraActions)
            val run = { action: SelectionAction ->
                action.onClick(selection.selected.toList())
                selection.clear()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .height(CompactHeroBarHeight)
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = selection::clear) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_exit))
                }
                Text(
                    text = pluralStringResource(R.plurals.selection_count, selection.count, selection.count),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                actions.take(InlineActions).forEach { action ->
                    IconButton(onClick = { run(action) }) {
                        Icon(action.icon, contentDescription = stringResource(action.label))
                    }
                }
                val overflow = actions.drop(InlineActions)
                if (overflow.isNotEmpty()) SelectionOverflow(overflow, run)
            }
        }
    }
}

@Composable
private fun SelectionOverflow(actions: List<SelectionAction>, run: (SelectionAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        run(action)
                    }
                )
            }
        }
    }
}
