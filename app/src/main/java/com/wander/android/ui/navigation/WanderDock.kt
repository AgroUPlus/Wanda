package com.wander.android.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.dp

/**
 * How far the dock travels (scaled by progress) as the player opens above it — the full height of
 * the card plus enough margin to clear its own shadow, so at full progress it is genuinely below
 * the visible screen rather than peeking a few dp short of it. It used to be a flat 10 dp, which
 * read as the dock twitching in place rather than making way for the player.
 */
private val RecedeDistance = DockRowHeight + 40.dp

/**
 * Exterior (bottom, facing the screen edge) vs interior (top, facing the mini player floating
 * above this) corners — the mirror image of the mini player's own shape in `PlayerSheet.kt`, same
 * two radii. Together the two cards read as one continuous pill pulled apart in the middle, round
 * on the outside, squared off where the gap between them is.
 *
 * That squared-off top edge only makes sense while there is something to face, though — with
 * nothing playing, this card is alone at the bottom exactly the way the mini player is alone on an
 * Artist or Settings page, and gets the same answer: every corner heads toward [AloneCorner], a
 * full stadium.
 */
private val ExteriorCorner = 32.dp
private val InteriorCorner = 14.dp
private val AloneCorner = 32.dp

/**
 * The dock surface container — always its own independent, shaped card now, whether or not a
 * track is playing. It used to be two different things: this `Surface` when idle, and a bare,
 * un-shaped `Row` sharing the mini player's own clip and shadow once a track existed — so the nav
 * row lost its own identity, and its corners, exactly when the mini player above it needed its
 * own distinct one instead. See `WanderAppDock.kt` for why it now always renders this way.
 *
 * [progress] is the player sheet's own *raw* open progress — `PlayerSheetState.rawProgress`, not
 * the clamped one — read only inside [graphicsLayer] so this never recomposes on a drag frame,
 * the same discipline `PlayerSheet` itself uses. Reusing the raw signal rather than animating a
 * second `Animatable` toward it is deliberate: `rawProgress` already carries the sheet's own
 * spring overshoot (see `PlayerSheetState.rawProgress`'s doc), so the dock's recede/fade inherits
 * that bounce for free, in lockstep with the sheet, instead of a second spring chasing a target
 * that is itself already mid-drag every frame — which would only ever lag half a beat behind it.
 *
 * [pairedWithMiniPlayer] is a plain composition-time boolean, not a per-frame signal, so its own
 * shape change (alone ↔ paired) is a normal animated value, eased on the same spec `PlayerSheet`
 * uses for the identical decision on its own side.
 */
@Composable
fun WanderDock(
    currentRoute: String?,
    query: String,
    items: List<DockItem>,
    onOpenItem: (DockItem) -> Unit,
    onOpenLibrary: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    pairedWithMiniPlayer: Boolean = false
) {
    val pairedness by animateFloatAsState(
        targetValue = if (pairedWithMiniPlayer) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "dockPairedness"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(
            topStart = lerpDp(AloneCorner, InteriorCorner, pairedness),
            topEnd = lerpDp(AloneCorner, InteriorCorner, pairedness),
            bottomStart = lerpDp(AloneCorner, ExteriorCorner, pairedness),
            bottomEnd = lerpDp(AloneCorner, ExteriorCorner, pairedness)
        ),
        shadowElevation = 6.dp,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                val p = progress()
                // Unclamped on the low side: a moment past fully-open in the sheet's own spring
                // (`rawProgress` briefly exceeding 1) reads here as the dock dipping a touch
                // further than its resting fade/recede before easing back — the bounce.
                alpha = (1f - p).coerceAtLeast(0f)
                translationY = RecedeDistance.toPx() * p
            }
    ) {
        WanderDockRow(
            currentRoute = currentRoute,
            query = query,
            items = items,
            onOpenItem = onOpenItem,
            onOpenLibrary = onOpenLibrary,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onListen = onListen
        )
    }
}
