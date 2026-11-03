package com.wander.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R
import kotlinx.coroutines.delay

/** What a notice is about, which picks its colours and badge. */
enum class NoticeKind { INFO, DONE, ERROR }

/**
 * A [SnackbarVisuals] that says what kind of notice it is. Any other visuals — every plain
 * `showSnackbar(message)` — show as [NoticeKind.INFO].
 */
class NoticeVisuals(
    override val message: String,
    val kind: NoticeKind = NoticeKind.INFO,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
) : SnackbarVisuals

/**
 * Every in-app notice: a tonal pill dropped in from under the status bar on the theme's spatial
 * spring, flicked back up to dismiss — Phalanx's notice, driven by the same [SnackbarHostState] the
 * screens already post to, so actions (Undo) and their results are unchanged.
 *
 * Replaces the stock snackbar, an inverse-surface bar that read as a white slab over a dark app.
 * The host times notices itself, as `SnackbarHost` would: 4 s, 10 s with an action, stretched by
 * the user's accessibility timeout.
 */
@Composable
fun WandaNoticeHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val data = hostState.currentSnackbarData
    // Kept while the exit animation runs, after the host already cleared it.
    val shown = rememberLastLive(data, live = data != null)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(data) {
        val current = data ?: return@LaunchedEffect
        val base = when (current.visuals.duration) {
            SnackbarDuration.Short -> SHORT_MILLIS
            SnackbarDuration.Long -> LONG_MILLIS
            SnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        val hasAction = current.visuals.actionLabel != null
        delay(accessibility?.calculateRecommendedTimeoutMillis(base, true, true, hasAction) ?: base)
        current.dismiss()
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = data != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { -it * 2 } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { -it * 2 } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth(), Alignment.TopCenter) {
            shown?.let { NoticeCard(it) }
        }
    }
}

@Composable
private fun NoticeCard(data: SnackbarData) {
    val kind = (data.visuals as? NoticeVisuals)?.kind ?: NoticeKind.INFO
    val colors = MaterialTheme.colorScheme
    val (container, content, accent) = when (kind) {
        NoticeKind.ERROR -> Triple(colors.errorContainer, colors.onErrorContainer, colors.error)
        NoticeKind.DONE -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        NoticeKind.INFO -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> if (delta < DISMISS_DRAG_PX) data.dismiss() }
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = accent, contentColor = container) {
                Icon(
                    imageVector = when (kind) {
                        NoticeKind.INFO -> Icons.Rounded.Info
                        NoticeKind.DONE -> Icons.Rounded.CheckCircle
                        NoticeKind.ERROR -> Icons.Rounded.ErrorOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp)
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = data::performAction) {
                    Text(label, color = if (kind == NoticeKind.INFO) accent else Color.Unspecified)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(onClick = data::dismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_dismiss))
                }
            }
        }
    }
}

private const val SHORT_MILLIS = 4_000L
private const val LONG_MILLIS = 10_000L

/** An upward flick this fast, per frame, puts the notice away. */
private const val DISMISS_DRAG_PX = -24f
