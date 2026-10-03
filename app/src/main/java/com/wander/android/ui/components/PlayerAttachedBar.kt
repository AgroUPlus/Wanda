package com.wander.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.player.PlayerSheetState
import com.wander.android.ui.components.player.PlayerSheetValue

/** The one card that sits on top of the mini-player, when anything is shared with anyone. */
internal sealed interface AttachedBar {
    data class InJam(val jam: Jam) : AttachedBar
    /** You are following someone. */
    data class Following(val session: ListenAlongSession) : AttachedBar
    /** Someone is following you. */
    data class Followed(val listeners: List<String>) : AttachedBar
}

/** Which bar applies, most involved first: a jam owns playback outright, following owns it too. */
internal fun attachedBarOf(jam: Jam?, session: ListenAlongSession?, listeners: List<String>): AttachedBar? =
    when {
        jam != null -> AttachedBar.InJam(jam)
        session != null -> AttachedBar.Following(session)
        listeners.isNotEmpty() -> AttachedBar.Followed(listeners)
        else -> null
    }

internal val AttachedBar.height: Dp
    get() = when (this) {
        is AttachedBar.InJam -> JamBarHeight
        is AttachedBar.Following -> ListenAlongBarHeight
        is AttachedBar.Followed -> ListenersBarHeight
    }

/**
 * How far the sheet travels before the bar has fully gone.
 *
 * Well short of the whole way: the bar sits where the expanding sheet is about to be, so it has to
 * be out of the way before the sheet's own content arrives underneath it.
 */
private const val HiddenByProgress = 0.3f

/**
 * Hosts the attached bar and animates it with the player.
 *
 * Two motions, layered. While the sheet is being dragged the bar follows the finger — it slides
 * down behind the mini-player and fades as the sheet rises, read straight from the sheet's
 * progress in the draw phase so it never recomposes per frame. Once the sheet *commits* to opening
 * (or a bar appears or goes because a session started or ended) a spring takes it the rest of the
 * way in or out, and removes it from the tree — so it cannot catch taps meant for Now Playing.
 */
@Composable
internal fun PlayerAttachedBarSlot(
    bar: AttachedBar?,
    showChrome: Boolean,
    sheetState: PlayerSheetState,
    modifier: Modifier = Modifier,
    content: @Composable (AttachedBar) -> Unit
) {
    // Held through the exit, so the bar animates out with what it last said rather than blank.
    var lastBar by remember { mutableStateOf(bar) }
    if (bar != null) lastBar = bar

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = bar != null && showChrome && sheetState.targetValue == PlayerSheetValue.COLLAPSED,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } +
            fadeIn(motion.defaultEffectsSpec()) +
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f, transformOrigin = BottomCenter),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } +
            fadeOut(motion.fastEffectsSpec()) +
            scaleOut(motion.fastSpatialSpec(), targetScale = 0.92f, transformOrigin = BottomCenter),
        modifier = modifier.graphicsLayer {
            val hidden = (sheetState.progress / HiddenByProgress).coerceIn(0f, 1f)
            alpha = 1f - hidden
            translationY = size.height * hidden
            transformOrigin = BottomCenter
            scaleX = 1f - 0.08f * hidden
            scaleY = scaleX
        }
    ) {
        val current = lastBar ?: return@AnimatedVisibility
        // Swapping kinds — a jam ending while someone still follows you — crossfades in place.
        AnimatedContent(
            targetState = current,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false))
            },
            label = "attachedBar"
        ) { shown -> content(shown) }
    }
}

private val BottomCenter = TransformOrigin(0.5f, 1f)
