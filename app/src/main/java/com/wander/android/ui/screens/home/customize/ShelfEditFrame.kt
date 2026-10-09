package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.layout.ShelfConfig

private const val HiddenAlpha = 0.38f

/**
 * One shelf in the customizer: its title, a show/hide switch, a settings button and the drag grip,
 * over a live preview of its tracks. A hidden shelf stays in place, dimmed, so it can be brought back.
 *
 * [dragHandle] is the reorder grip supplied by the list, as in the queue. [onOpenSettings] is null
 * for a shelf with nothing to configure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShelfEditFrame(
    section: HomeSection,
    config: ShelfConfig,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: (() -> Unit)?,
    dragHandle: Modifier.() -> Modifier,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "shelfFrameColor"
    )
    val toggleLabel = stringResource(R.string.home_shelf_show, section.title)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        tonalElevation = if (isDragging) 6.dp else 0.dp,
        shadowElevation = if (isDragging) 6.dp else 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = toggleLabel }
                )
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.home_shelf_settings, section.title)
                        )
                    }
                }
                IconButton(onClick = {}, shapes = IconButtonDefaults.shapes(), modifier = Modifier.dragHandle()) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.queue_reorder, section.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(modifier = Modifier.alpha(if (config.enabled) 1f else HiddenAlpha).padding(bottom = 16.dp)) {
                ShelfPreview(section)
            }
        }
    }
}
