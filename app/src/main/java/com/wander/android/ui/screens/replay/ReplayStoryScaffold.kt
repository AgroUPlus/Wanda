package com.wander.android.ui.screens.replay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The story shell: a pager of cards, a rail above them, and the gestures that drive both.
 *
 * ## Auto-advance, and when it must not
 *
 * Cards move on by themselves, because a recap that needs eleven deliberate taps is a form. Three
 * things stop the clock: a finger held on the screen, the last card (which is the outro and has
 * buttons on it), and reduced motion.
 *
 * The last is not a nicety. "Remove animations" is set by people who cannot follow moving content,
 * and a story that advances on a timer is exactly that. With it on, the deck only moves when it is
 * tapped — everything else about the story still works.
 */
@Composable
internal fun ReplayStoryScaffold(
    deck: List<ReplayCard>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    card: @Composable (ReplayCard) -> Unit
) {
    if (deck.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { deck.size })
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    var isHeld by remember { mutableStateOf(false) }

    val index = pagerState.currentPage
    val isLastCard = index == deck.lastIndex

    // Keyed on the page so the timer restarts from the top of each card rather than carrying a
    // part-spent countdown into the next one.
    LaunchedEffect(index, isHeld, reducedMotion, isLastCard) {
        if (reducedMotion || isHeld || isLastCard) return@LaunchedEffect
        delay(CardDurationMs)
        pagerState.animateScrollToPage(index + 1)
    }

    // Back steps through the story, and only leaves from the first card — the same shape as every
    // other stack in the app, so predictive back does not surprise.
    BackHandler {
        if (index == 0) onDismiss() else scope.launch {
            pagerState.animateScrollToPage(index - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .replayTapAndHold(
                onPreviousTap = {
                    if (index > 0) scope.launch { pagerState.animateScrollToPage(index - 1) }
                },
                onNextTap = {
                    if (index < deck.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(index + 1) }
                    }
                },
                onHoldChange = { isHeld = it }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The tap handler owns horizontal movement; leaving the pager draggable as well meant
            // a tap that travelled a few pixels landed as a half-swipe that sprang back.
            userScrollEnabled = false
        ) { page ->
            card(deck[page])
        }

        // Inside `safeDrawingPadding` so the rail clears the status bar and the cutout, and drawn
        // over the pager so it is never scrolled away from.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = RailGutter, vertical = RailTopPadding)
        ) {
            ReplayProgressRail(cardCount = deck.size, currentIndex = index)
        }
    }
}

/** Long enough to read a headline and a number, short enough not to feel like waiting. */
private const val CardDurationMs = 5_000L
private val RailGutter = 16.dp
private val RailTopPadding = 12.dp
