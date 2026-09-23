package com.wander.android.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.rememberHaptics

/**
 * Play all and Shuffle for the library's tracks, as floating action buttons: a medium Play FAB in
 * the thumb's corner with a small Shuffle FAB stacked above it. They grow out of the corner when
 * there is something to play and shrink back into it when there isn't.
 */
@Composable
internal fun LibraryPlayShufflePill(
    visible: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val corner = TransformOrigin(1f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = bottomInset + 16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.4f, transformOrigin = corner),
            exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.4f, transformOrigin = corner)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        haptics.confirmed()
                        onShuffle()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.library_shuffle_all)
                    )
                }
                MediumFloatingActionButton(
                    onClick = {
                        haptics.confirmed()
                        onPlayAll()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation()
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.common_play_all),
                        modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize)
                    )
                }
            }
        }
    }
}
