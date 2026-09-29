package com.wander.android.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset

/** Where the trigger sits, bottom-right corner — only the whole-widget appear/disappear uses this. */
private val CornerOrigin = TransformOrigin(1f, 1f)

/**
 * Home's one floating action: a "start radio" FAB that opens into a fan of mood presets.
 *
 * Built on Material 3's own expandable-FAB components — `FloatingActionButtonMenu`,
 * `ToggleFloatingActionButton` and `FloatingActionButtonMenuItem` — rather than a hand-rolled
 * `Column`/`AnimatedVisibility` stack: those own the exact spec behaviour (the trigger stays put
 * and only its icon and shape animate; the items fan out from it on the platform's own staggered
 * spring) that a bespoke version kept getting subtly wrong, including the trigger visibly drifting
 * on toggle.
 *
 * Replaces the old pairing of a plain icon-only radio FAB and a separate flat row of mood chips at
 * the top of Home (`MoodMatrixCard`, now retired) with this single control. Picking any pill
 * (including "Surprise me", the instant/no-mood radio the FAB used to do on every tap) starts that
 * radio and collapses the menu.
 *
 * Hidden while the player is anywhere but docked, and while you are anywhere but Home — see
 * [visible]'s callers in `WanderAppOverlays.kt`: a button pinned over a full-screen player is in
 * the way of its controls, and a fan of pills mid-expansion is worse still to have parked over a
 * screen you've navigated away from, so expansion always collapses first (see the `LaunchedEffect`
 * below) rather than being carried off-screen open.
 */
@Composable
internal fun RadioMoodFab(
    isStarting: Boolean,
    visible: Boolean,
    moods: List<MoodPreset>,
    playingMoodKey: String?,
    onInstantRadio: () -> Unit,
    onSelectMood: (MoodPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) expanded = false }

    val pulseTransition = rememberInfiniteTransition(label = "radio-fab")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "radio-pulse"
    )

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        // Slow spatial in, fast out — arriving is the button announcing itself, leaving is it
        // getting out of the way of something the user has already started. Only the whole
        // widget's own appear/disappear pivots from the corner; nothing inside it should.
        enter = scaleIn(motion.slowSpatialSpec(), transformOrigin = CornerOrigin) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec(), transformOrigin = CornerOrigin) +
            fadeOut(motion.fastEffectsSpec()),
        modifier = modifier
    ) {
        FloatingActionButtonMenu(
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it }
                ) {
                    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            (fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f))
                                .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f))
                        },
                        label = "radio-trigger-icon"
                    ) { isExpanded ->
                        if (isExpanded) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = if (isStarting) {
                                    stringResource(R.string.home_radio_starting)
                                } else {
                                    stringResource(R.string.home_radio_open_picker)
                                },
                                modifier = Modifier.graphicsLayer { alpha = if (isStarting) pulse else 1f }
                            )
                        }
                    }
                }
            }
        ) {
            moods.forEach { mood ->
                FloatingActionButtonMenuItem(
                    onClick = { onSelectMood(mood); expanded = false },
                    icon = { Icon(mood.icon(), contentDescription = null) },
                    text = { Text(mood.label()) },
                    containerColor = if (mood.key == playingMoodKey) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (mood.key == playingMoodKey) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            FloatingActionButtonMenuItem(
                onClick = { onInstantRadio(); expanded = false },
                icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                text = { Text(stringResource(R.string.home_radio_surprise_me)) },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** The localized label for a [MoodPreset] — the one place its [MoodPreset.key] becomes text. */
@Composable
private fun MoodPreset.label(): String = stringResource(
    when (key) {
        "late_night_chill" -> R.string.mood_preset_late_night_chill
        "focus_flow" -> R.string.mood_preset_focus_flow
        "morning_energizer" -> R.string.mood_preset_morning_energizer
        "high_voltage" -> R.string.mood_preset_high_voltage
        else -> R.string.mood_preset_late_night_chill
    }
)

/** One glyph per preset, standing in for its tempo/energy at a glance. */
private fun MoodPreset.icon(): ImageVector = when (key) {
    "late_night_chill" -> Icons.Rounded.Nightlight
    "focus_flow" -> Icons.Rounded.CenterFocusStrong
    "morning_energizer" -> Icons.Rounded.WbSunny
    "high_voltage" -> Icons.Rounded.Bolt
    else -> Icons.Rounded.Radio
}
