package com.wander.android.ui.screens.queue

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** A section of the queue: its name, and how many tracks are in it on a small tonal badge. */
@Composable
internal fun QueueSectionTitle(@StringRes title: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    ) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized)
        CountBadge(count)
    }
}

/**
 * The header of the played tracks, which folds them away. Closed by default: the queue is about
 * what comes next, and the past is one tap off rather than in the way.
 */
@Composable
internal fun QueuePlayedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "queuePlayedChevron"
    )
    val state = stringResource(if (expanded) R.string.common_expanded else R.string.common_collapsed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.queue_section_played),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CountBadge(count)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn)
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
