package com.wander.android.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** How small a sheet gets at the far end of a back swipe, before it commits and slides away. */
private const val BackMinScale = 0.9f

/**
 * Every bottom sheet in the app. A [ModalBottomSheet] that answers a back swipe itself, so it can
 * shrink toward its foot as the finger drags it away instead of the stock sheet's plain slide.
 *
 * The back swipe shrinks the sheet toward its foot with the same growing resistance as every other
 * drag in the app ([backResistance]); letting go past the gesture's threshold slides it away,
 * cancelling springs it back.
 *
 * [content] receives `animatedDismiss`, an alternative to [onDismissRequest] that a menu action
 * should call instead: it runs the sheet's own hide animation first and only invokes
 * [onDismissRequest] once that settles. A `MenuAction.onClick` that calls [onDismissRequest]
 * directly rips the whole composable out of composition the instant the tap lands — the caller's
 * `onDismiss` is usually just a piece of state going `null` — which skips the sheet's collapse
 * animation entirely and reads as the menu cutting rather than closing. Swiping down or tapping the
 * scrim doesn't have this problem: those already go through [ModalBottomSheet]'s own hide path
 * before [onDismissRequest] fires, same as the back-gesture handler below.
 */
@Composable
fun WandaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.(animatedDismiss: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var backProgress by remember { mutableFloatStateOf(0f) }

    val animatedDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // Matches `QueueDrawer`'s own surface color, so the contextual menu opened from the player
        // doesn't read as a different, mismatched layer over the same screen.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // The handler below takes the back gesture instead, so the sheet can report its progress.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        modifier = modifier.graphicsLayer {
            val scale = lerp(1f, BackMinScale, backResistance(backProgress))
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
        PredictiveBackHandler(enabled = true) { progress ->
            try {
                progress.collect { backProgress = it.progress }
                animatedDismiss()
            } catch (_: CancellationException) {
                backProgress = 0f
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            content = { content(animatedDismiss) }
        )
    }
}
