package com.wander.android.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressScale

/**
 * Expressive floating action capsule offering Play All and Shuffle actions.
 * Anchored above the bottom dock / mini player in library track lists.
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
    val playInteraction = remember { MutableInteractionSource() }
    val playScale by rememberPressScale(playInteraction, label = "pillPlayScale")
    val shuffleInteraction = remember { MutableInteractionSource() }
    val shuffleScale by rememberPressScale(shuffleInteraction, label = "pillShuffleScale")

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomInset + 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.82f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.82f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Button(
                        onClick = {
                            haptics.confirmed()
                            onPlayAll()
                        },
                        interactionSource = playInteraction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .scale(playScale)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.common_play_all),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_play),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    FilledTonalButton(
                        onClick = {
                            haptics.confirmed()
                            onShuffle()
                        },
                        interactionSource = shuffleInteraction,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .scale(shuffleScale)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.library_shuffle_all),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_shuffle),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
