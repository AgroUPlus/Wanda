package com.wander.android.ui.screens.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.ui.components.rememberHaptics
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

/** The rail's letters, top to bottom: everything that is not A to Z files under `#`. */
private val RailLetters: List<Char> = listOf('#') + ('A'..'Z')

/** Shorter than this a list fits a few flicks, and a column of letters beside it is clutter. */
private const val MIN_RAIL_ITEMS = 30

/** How much room a list leaves at its end for the rail to sit in beside its rows. */
internal val LetterRailGutter = 16.dp

/** The rail letter a fast-scroll letter ([com.wander.android.ui.components.fastScrollLetter]) falls under. */
internal fun railLetter(letter: String?): Char? {
    val first = letter?.singleOrNull() ?: return null
    return if (first in 'A'..'Z') first else '#'
}

/**
 * Where each rail letter's run starts in [items], for an alphabetical list whose letters
 * [letterFor] names; null for an order with no letters, or a list too short to need the rail.
 *
 * [order] keys the scan alongside the list — [letterFor] is a fresh lambda every composition.
 */
@Composable
internal fun <T> rememberLetterStarts(items: List<T>, order: Any?, letterFor: ((T) -> String?)?): Map<Char, Int>? {
    val currentLetterFor by rememberUpdatedState(letterFor)
    return remember(items, order, letterFor != null) {
        val letterOf = currentLetterFor ?: return@remember null
        if (items.size < MIN_RAIL_ITEMS) return@remember null
        val starts = HashMap<Char, Int>()
        items.forEachIndexed { index, item -> railLetter(letterOf(item))?.let { starts.getOrPut(it) { index } } }
        starts
    }
}

/**
 * An A–Z column down a list's end edge: touch or drag over a letter to jump to the first item
 * filed under it. A letter with nothing under it is dimmed and jumps to the next one that has.
 *
 * The whole column takes the touch, not each letter: a finger sliding down it is one gesture, and
 * per-letter click targets would each claim and drop it. TalkBack gets one action per letter.
 *
 * @param letterAt the letter of the item at the top of the list now, which the rail marks.
 */
@Composable
internal fun LetterRail(
    starts: Map<Char, Int>,
    letterAt: () -> Char?,
    onJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val targets = remember(starts) { letterTargets(starts) }
    val currentTargets by rememberUpdatedState(targets)
    val currentJump by rememberUpdatedState(onJump)
    val currentLetterAt by rememberUpdatedState(letterAt)
    var touched by remember { mutableStateOf<Char?>(null) }
    // Read through derivedStateOf: the list's first item changes on every row scrolled past, the
    // letter it is filed under only once per run.
    val active by remember { derivedStateOf { touched ?: currentLetterAt() } }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // Conflated: a fast drag crosses several letters a frame, and only the newest one matters.
    val jumps = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(jumps) { jumps.consumeAsFlow().collect { currentJump(it) } }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .railGesture(
                onLetter = { at ->
                    val letter = RailLetters[at]
                    if (letter != touched) {
                        if (touched != null) haptics.tick()
                        touched = letter
                        currentTargets[at]?.let(jumps::trySend)
                    }
                },
                onRelease = { touched = null }
            )
    ) {
        RailLetters.forEachIndexed { at, letter ->
            RailLetter(
                letter = letter,
                isActive = letter == active,
                hasItems = letter in starts,
                target = targets[at],
                onJump = { scope.launch { currentJump(it) } }
            )
        }
    }
}

/**
 * For each rail letter, the index its jump lands on: its own run's start, or else the next
 * letter's that has one, or failing that the last one before it.
 */
private fun letterTargets(starts: Map<Char, Int>): List<Int?> =
    RailLetters.indices.map { at ->
        (at until RailLetters.size).firstNotNullOfOrNull { starts[RailLetters[it]] }
            ?: (at downTo 0).firstNotNullOfOrNull { starts[RailLetters[it]] }
    }

/**
 * The column's one touch gesture: [onLetter] gets the index of the letter under the finger on
 * the down and on every move while it stays pressed, [onRelease] once it lifts.
 */
private fun Modifier.railGesture(onLetter: (Int) -> Unit, onRelease: () -> Unit): Modifier =
    pointerInput(Unit) {
        fun letterAt(y: Float): Int =
            (y / size.height * RailLetters.size).toInt().coerceIn(0, RailLetters.lastIndex)
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onLetter(letterAt(down.position.y))
            var change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            while (change != null && change.pressed) {
                change.consume()
                onLetter(letterAt(change.position.y))
                change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            }
            onRelease()
        }
    }

/** One letter of the rail; [target] is null when the list has no letters at all to jump to. */
@Composable
private fun RailLetter(
    letter: Char,
    isActive: Boolean,
    hasItems: Boolean,
    target: Int?,
    onJump: (Int) -> Unit
) {
    val label = stringResource(R.string.library_jump_to_letter, letter.toString())
    val scheme = MaterialTheme.colorScheme
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
        color = when {
            isActive -> scheme.primary
            hasItems -> scheme.onSurfaceVariant
            else -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier.semantics {
            contentDescription = label
            role = Role.Button
            if (target != null) onClick(label) { onJump(target); true }
        }
    )
}
