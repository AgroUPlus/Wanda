package com.wander.android.ui.screens.replay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

/**
 * The story shell: a pager of cards, a rail above them, and the gestures that drive both.
 *
 * ## Who owns the current card
 *
 * [index] does, not the pager. The pager only follows it. Driving the story from
 * `pagerState.currentPage` meant the auto-advance's scroll was launched from an effect keyed on
 * that same page — and `currentPage` flips at the halfway point of a scroll, which cancelled the
 * effect and left the story stuck between two cards. Now a scroll can be interrupted only by a
 * newer target, and it always resumes toward it.
 *
 * ## Auto-advance, and when it must not
 *
 * Cards move on by themselves, because a recap that needs eleven deliberate taps is a form. Four
 * things stop the clock: a finger held on the screen, the pause button, the last card (which is
 * the outro and has buttons on it), and reduced motion.
 *
 * The last is not a nicety. "Remove animations" is set by people who cannot follow moving content,
 * and a story that advances on a timer is exactly that. With it on, the deck only moves when it is
 * tapped — everything else about the story still works.
 *
 * ## Colour
 *
 * The flat background is drawn here rather than by each card, from [replayPaletteFor], so it
 * crossfades between cards instead of sliding with them, and so the rail and controls always sit
 * in the same content colour as the card under them.
 */
@Composable
internal fun ReplayStoryScaffold(
    deck: List<ReplayCard>,
    onDismiss: () -> Unit,
    /** Printed along the bottom of a shared card, and only there. */
    shareFooter: String,
    modifier: Modifier = Modifier,
    card: @Composable (ReplayCard) -> Unit
) {
    if (deck.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { deck.size })
    val reducedMotion = LocalReducedMotion.current
    var isHeld by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val isLastCard = index == deck.lastIndex
    val capture = rememberGraphicsLayer()
    val measurer = rememberTextMeasurer()
    val footerStyle = MaterialTheme.typography.labelLargeEmphasized
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The story's own, like the inbox's: it covers the shell, and a share failure belongs to it.
    val snackbar = remember { SnackbarHostState() }

    fun goTo(target: Int) {
        index = target.coerceIn(0, deck.lastIndex)
    }

    // The pager follows the story. A newer target cancels an older scroll mid-flight, and the new
    // one carries on from wherever the pages are — so nothing ever rests between two cards.
    LaunchedEffect(index) {
        if (reducedMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
    }

    // The card's clock, fresh per card so a part-spent countdown never carries into the next one.
    // It is also what the rail draws, so the bar and the advance can never disagree.
    val clock = remember(index) { Animatable(0f) }

    LaunchedEffect(index, isHeld, isPaused, reducedMotion, isLastCard) {
        // Nothing counts down on these, so the segment reads as reached rather than stuck empty.
        if (reducedMotion || isLastCard) {
            clock.snapTo(1f)
            return@LaunchedEffect
        }
        // Held or paused: the effect restarts on release and resumes from where the fill stopped,
        // with only the remaining time left to run.
        if (isHeld || isPaused) return@LaunchedEffect
        val remaining = ((1f - clock.value) * CardDurationMs).toInt()
        clock.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
        goTo(index + 1)
    }

    // Back steps through the story, and only leaves from the first card — the same shape as every
    // other stack in the app, so predictive back does not surprise.
    BackHandler {
        if (index == 0) onDismiss() else goTo(index - 1)
    }

    val palette = replayPaletteFor(deck[index])
    val background by animateColorAsState(
        targetValue = palette.container,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "replayBackground"
    )
    val content by animateColorAsState(
        targetValue = palette.content,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "replayContent"
    )

    CompositionLocalProvider(
        LocalContentColor provides content,
        LocalReplayContainer provides background
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Records the card and its flat colour — not the rail or the controls — so
                    // Share has a clean poster to hand out.
                    .replayCapture(
                        layer = capture,
                        footer = shareFooter,
                        measurer = measurer,
                        style = footerStyle,
                        background = { background },
                        content = { content }
                    )
                    .replayTapAndHold(
                        onPreviousTap = { goTo(index - 1) },
                        onNextTap = { goTo(index + 1) },
                        onHoldChange = { isHeld = it }
                    )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // The tap handler owns horizontal movement; leaving the pager draggable as well
                    // meant a tap that travelled a few pixels landed as a half-swipe that sprang
                    // back.
                    userScrollEnabled = false
                ) { page ->
                    card(deck[page])
                }
            }

            // Siblings of the tap surface rather than children, so pressing a control is never
            // also a tap on the story. Inside `safeDrawingPadding` to clear the status bar and the
            // cutout.
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = RailGutter, vertical = RailTopPadding)
            ) {
                ReplayProgressRail(
                    cardCount = deck.size,
                    currentIndex = index,
                    currentProgress = { clock.value }
                )
            }
            ReplayStoryControls(
                isPaused = isPaused,
                container = background,
                // Nothing to pause where nothing counts down.
                canPause = !reducedMotion && !isLastCard,
                onTogglePause = { isPaused = !isPaused },
                onClose = onDismiss,
                // The outro is buttons, not a poster.
                canShare = !isLastCard,
                onShare = {
                    // The story would otherwise move on behind the share sheet.
                    isPaused = true
                    scope.launch {
                        if (!shareReplayCard(context, capture)) {
                            snackbar.showSnackbar(context.getString(R.string.replay_share_failed))
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(end = ControlsGutter, top = ControlsTop)
            )
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
            )
        }
    }
}

/**
 * Long enough to read a headline, a number and the list under it without holding the screen.
 * 5s proved too quick for the list cards.
 */
private const val CardDurationMs = 8_000
private val RailGutter = 16.dp
private val RailTopPadding = 12.dp
private val ControlsGutter = 8.dp

/** Under the rail, level with the year picker on the other side. */
private val ControlsTop = 20.dp
