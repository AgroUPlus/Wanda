package com.wander.android.ui.screens.home.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeShelfStates
import com.wander.android.ui.screens.home.HomeUiState
import com.wander.android.ui.screens.home.HomeViewModel
import com.wander.android.ui.screens.home.layout.ShelfConfig
import com.wander.android.ui.screens.home.layout.ShelfSuggestions
import com.wander.android.ui.screens.home.layout.ShelfUsage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Everything the customizer needs on the Home screen, so `HomeScreen` only has to place it. */
internal class HomeEditor(
    val order: List<HomeSection>,
    val reorderState: ReorderableLazyListState,
    val settingsFor: String?,
    val openSettings: (String?) -> Unit,
    val addingShelf: Boolean,
    val setAddingShelf: (Boolean) -> Unit,
    /** Opens a new shelf's settings once the saved layout has caught up with it. */
    val openWhenSaved: (String) -> Unit,
    /** How often songs were played from each shelf; see [ShelfUsageStore]. */
    val usage: ShelfUsage,
    /** The shelves on Home that nothing has been played from, once there is enough history to say. */
    val rarelyUsed: List<String>
)

@Composable
internal fun rememberHomeEditor(state: HomeUiState, viewModel: HomeViewModel, listState: LazyListState): HomeEditor {
    BackHandler(enabled = state.editing, onBack = viewModel.layoutActions::stop)

    // The list is reordered here at once and the saved layout follows: the saved copy arrives a
    // beat later, and reading only that made a dragged shelf jump back a slot, as the queue found.
    var order by remember { mutableStateOf(state.editorSections) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        val fromIndex = order.indexOfFirst { it.id == fromId }
        val toIndex = order.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        viewModel.layoutActions.move(fromId, toId)
    }
    val dragging = reorderState.isAnyItemDragging
    LaunchedEffect(state.editorSections, dragging) { if (!dragging) order = state.editorSections }

    var settingsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var addingShelf by rememberSaveable { mutableStateOf(false) }
    // The layout reaches this screen a beat after it is saved, and a settings sheet for a shelf it
    // does not know yet would close itself, so a new shelf's sheet waits for the layout.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pending, state.layout) {
        val id = pending ?: return@LaunchedEffect
        if (state.layout.any { it.id == id }) {
            settingsFor = id
            pending = null
        }
    }
    val usage by viewModel.shelfUsage.usage.collectAsStateWithLifecycle(ShelfUsage())
    val rarelyUsed = remember(order, usage) {
        ShelfSuggestions.rarelyUsed(order.map { it.id }, usage, System.currentTimeMillis())
    }
    return HomeEditor(order, reorderState, settingsFor, { settingsFor = it }, addingShelf, { addingShelf = it }, { pending = it }, usage, rarelyUsed)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.homeEditorShelves(
    editor: HomeEditor,
    state: HomeUiState,
    viewModel: HomeViewModel,
    states: HomeShelfStates
) {
    itemsIndexed(editor.order, key = { _, section -> section.id }, contentType = { _, _ -> "editable-shelf" }) { index, section ->
        val haptics = rememberHaptics()
        val previous = editor.order.getOrNull(index - 1)
        val next = editor.order.getOrNull(index + 1)
        ReorderableItem(editor.reorderState, key = section.id) { isDragging ->
            ShelfEditFrame(
                section = section,
                config = state.layout.firstOrNull { it.id == section.id } ?: ShelfConfig(section.id),
                isDragging = isDragging,
                viewModel = viewModel,
                states = states,
                rarelyUsed = section.id in editor.rarelyUsed,
                onTap = { editor.openSettings(section.id) },
                onMoveUp = previous?.let { { viewModel.layoutActions.move(section.id, it.id) } },
                onMoveDown = next?.let { { viewModel.layoutActions.move(section.id, it.id) } },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .longPressDraggableHandle(
                        onDragStarted = { haptics.heldDown() },
                        onDragStopped = { haptics.settled() }
                    )
            )
        }
    }
    item(key = "add-shelf", contentType = "add-shelf") {
        FilledTonalButton(
            onClick = { editor.setAddingShelf(true) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp).animateItem()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.home_shelf_add))
        }
    }
}

@Composable
internal fun HomeEditorSettingsSheet(editor: HomeEditor, state: HomeUiState, viewModel: HomeViewModel) {
    val id = editor.settingsFor ?: return
    val section = state.editorSections.firstOrNull { it.id == id }
    // The shelf can vanish under the open sheet — a refresh dropped it — and a sheet for nothing should go.
    if (section == null) {
        editor.openSettings(null)
        return
    }
    ShelfSettingsSheet(
        section = section,
        config = state.layout.firstOrNull { it.id == id } ?: ShelfConfig(id),
        onStyle = { viewModel.layoutActions.setStyle(id, it) },
        onCount = { viewModel.layoutActions.setCount(id, it) },
        libraryGenres = viewModel.genres.collectAsStateWithLifecycle().value,
        onCategory = { viewModel.layoutActions.toggleCategory(id, it) },
        onLanguage = { viewModel.layoutActions.toggleLanguage(id, it) },
        plays = editor.usage.takeIf { it.since != 0L }?.playsFrom(id),
        sources = state.sources,
        onSource = { viewModel.layoutActions.toggleSource(id, it) },
        onRemove = {
            viewModel.layoutActions.remove(id)
            editor.openSettings(null)
        },
        onDismiss = { editor.openSettings(null) }
    )
}

@Composable
internal fun HomeEditorAddSheet(editor: HomeEditor, state: HomeUiState, viewModel: HomeViewModel) {
    if (!editor.addingShelf) return
    AddShelfSheet(
        removed = state.removedSections,
        extras = state.availableExtras,
        onRestore = viewModel.layoutActions::restore,
        onAddExtra = viewModel.layoutActions::addExtra,
        onCreateGenre = {
            editor.openWhenSaved(viewModel.layoutActions.addGenreShelf())
            editor.setAddingShelf(false)
        },
        onDismiss = { editor.setAddingShelf(false) }
    )
}
