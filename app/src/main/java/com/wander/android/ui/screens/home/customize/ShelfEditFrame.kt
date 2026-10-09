package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.ServiceProblem
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.HomeShelfStates
import com.wander.android.ui.screens.home.HomeViewModel
import com.wander.android.ui.screens.home.LocalHomeEditing
import com.wander.android.ui.screens.home.ShelfOrigin
import com.wander.android.ui.screens.home.shelfOrigin
import com.wander.android.ui.screens.home.layout.ShelfConfig

private const val LiftedScale = 1.03f

/**
 * One shelf in the customizer: the shelf itself, drawn as on Home, in a tonal card. Hold it to pick
 * it up and drag; tap it to edit it. Nothing else is drawn — the whole card is the control.
 *
 * The list's long-press-to-drag handle arrives in [modifier]. [onMoveUp] and [onMoveDown] are the same
 * reorder offered to accessibility services, which cannot long-press and drag.
 */
@Composable
internal fun ShelfEditFrame(
    section: HomeSection,
    config: ShelfConfig,
    isDragging: Boolean,
    rarelyUsed: Boolean,
    problem: ServiceProblem?,
    viewModel: HomeViewModel,
    states: HomeShelfStates,
    onTap: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(if (isDragging) LiftedScale else 1f, motion.fastSpatialSpec(), label = "shelfLift")
    val lift by animateDpAsState(if (isDragging) 16.dp else 0.dp, motion.fastSpatialSpec(), label = "shelfShadow")
    val origin = shelfOrigin(section.id)
    val resting = origin.tint(MaterialTheme.colorScheme.surfaceContainer)
    val container by animateColorAsState(
        if (isDragging) origin.tint(MaterialTheme.colorScheme.surfaceContainerHighest) else resting,
        motion.fastEffectsSpec(),
        label = "shelfColor"
    )
    val name = shelfName(section, config)
    val editLabel = stringResource(R.string.home_shelf_edit, name)
    val moveUp = stringResource(R.string.home_shelf_move_up)
    val moveDown = stringResource(R.string.home_shelf_move_down)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        shadowElevation = lift,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shelfTap(onTap)
            .semantics(mergeDescendants = true) {
                onClick(label = editLabel) {
                    onTap()
                    true
                }
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction(moveUp) { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction(moveDown) { it(); true } }
                )
            }
    ) {
        Box {
            CompositionLocalProvider(
                LocalHomeEditing provides true,
                LocalViewConfiguration provides rememberHeldNeverConfiguration()
            ) {
                if (section.isEmpty) {
                    EmptyShelf(name, emptyShelfMessage(config, problem))
                } else {
                    // A new layout fades and grows in over the old one, and the card follows the
                    // size change on the same spring, so changing a shelf's look reads as one motion.
                    AnimatedContent(
                        targetState = section.style,
                        transitionSpec = {
                            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                                fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                        },
                        label = "shelfLayout"
                    ) { style ->
                        ShelfBody(section.copy(style = style), viewModel, states, Modifier.padding(vertical = 14.dp))
                    }
                }
            }
            OriginBadge(origin, Modifier.align(Alignment.TopEnd).padding(10.dp))
            AnimatedVisibility(visible = rarelyUsed, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = stringResource(R.string.home_shelf_rarely_used),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** A shelf that has nothing to show yet: its name and what to do about it. */
@Composable
private fun EmptyShelf(name: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The tint a shelf's frame takes from where its songs come from; the library keeps the neutral [base]. */
@Composable
internal fun ShelfOrigin.tint(base: Color): Color = when (this) {
    ShelfOrigin.LIBRARY -> base
    ShelfOrigin.YOUTUBE_MUSIC -> lerp(base, MaterialTheme.colorScheme.tertiaryContainer, TintStrength)
    ShelfOrigin.AGRO -> lerp(base, MaterialTheme.colorScheme.primaryContainer, TintStrength)
    ShelfOrigin.FRIENDS -> lerp(base, MaterialTheme.colorScheme.secondaryContainer, TintStrength)
}

@Composable
internal fun ShelfOrigin.accent(): Color = when (this) {
    ShelfOrigin.LIBRARY -> MaterialTheme.colorScheme.secondaryContainer
    ShelfOrigin.YOUTUBE_MUSIC -> MaterialTheme.colorScheme.tertiaryContainer
    ShelfOrigin.AGRO -> MaterialTheme.colorScheme.primaryContainer
    ShelfOrigin.FRIENDS -> MaterialTheme.colorScheme.secondaryContainer
}

/** A small label for shelves that come from somewhere other than the library. */
@Composable
private fun OriginBadge(origin: ShelfOrigin, modifier: Modifier = Modifier) {
    val label = when (origin) {
        ShelfOrigin.LIBRARY -> return
        ShelfOrigin.YOUTUBE_MUSIC -> R.string.origin_youtube_music
        ShelfOrigin.AGRO -> R.string.origin_agro
        ShelfOrigin.FRIENDS -> R.string.origin_friends
    }
    Surface(shape = CircleShape, color = origin.accent(), modifier = modifier) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private const val TintStrength = 0.55f
