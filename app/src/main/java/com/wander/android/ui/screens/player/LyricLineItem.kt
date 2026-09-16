package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.LyricLine
import com.wander.android.data.repository.LrcParser

/**
 * Renders a single lyric line with smooth Material 3 Expressive motion between lines and
 * progressive word-by-word karaoke illumination.
 */
@Composable
fun LyricLineItem(
    line: LyricLine,
    nextLineTimestampMs: Long?,
    isActive: Boolean,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    letterByLetterEnabled: Boolean = true
) {
    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.40f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "lyricAlpha"
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (isActive) 10.dp else 6.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lyricPadding"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "lyricColor"
    )

    val contentModifier = modifier
        .fillMaxWidth()
        // The active line swaps to a bigger style below, which can push it from one wrapped line
        // to two. Without this, that change in line count landed as a hard cut — the row's height
        // jumped and every line below it hopped to a new position in the same frame.
        //
        // This used to run alongside an independent `graphicsLayer` scale (0.97 -> 1.03) on top of
        // the font itself jumping from titleMedium to titleLarge/ExtraBold — three animations
        // converging on one transition, each settling on its own schedule, which is what made the
        // line visibly overshoot as it grew. The size change and the font change are the real
        // transition; the scale was decoration on top of it and is gone.
        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
        .clickable { onSeek(line.timestampMs) }
        .padding(vertical = verticalPadding, horizontal = 16.dp)
        .graphicsLayer { alpha = lineAlpha }

    if (!isActive) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = contentModifier
        )
        return
    }

    // Active line: progressive word-by-word highlight
    val words = remember(line, nextLineTimestampMs) {
        LrcParser.resolveWords(line, nextLineTimestampMs)
    }

    if (words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = contentModifier
        )
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val upcomingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val syncPositionMs = currentPositionMs + LyricsLeadOffsetMs

    val annotatedText = remember(words, syncPositionMs, primaryColor, upcomingColor, letterByLetterEnabled) {
        buildAnnotatedString {
            words.forEachIndexed { wordIdx, word ->
                val hasTrailingSpace = wordIdx < words.size - 1
                when {
                    syncPositionMs >= word.endMs -> {
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                            append(word.text)
                        }
                        if (hasTrailingSpace) {
                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Normal)) {
                                append(" ")
                            }
                        }
                    }
                    syncPositionMs < word.startMs -> {
                        withStyle(SpanStyle(color = upcomingColor, fontWeight = FontWeight.Medium)) {
                            append(word.text)
                        }
                        if (hasTrailingSpace) {
                            withStyle(SpanStyle(color = upcomingColor, fontWeight = FontWeight.Normal)) {
                                append(" ")
                            }
                        }
                    }
                    !letterByLetterEnabled -> {
                        // Word is actively being sung, but the per-character sweep is off: light
                        // the whole word the instant it starts rather than progressing through it.
                        // This branch only runs once the earlier two have ruled out "not yet sung"
                        // and "already sung" — `when` still checks top to bottom, but by the time a
                        // word reaches here `syncPositionMs` is already known to sit inside its
                        // [startMs, endMs) window, which is exactly "currently being sung".
                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                            append(word.text)
                        }
                        if (hasTrailingSpace) {
                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Normal)) {
                                append(" ")
                            }
                        }
                    }
                    else -> {
                        // Word is actively being sung: progress letter by letter with speed adjusted for spaces and commas
                        val wordDuration = (word.endMs - word.startMs).coerceAtLeast(60L)
                        val elapsed = syncPositionMs - word.startMs
                        val wordProgress = (elapsed.toFloat() / wordDuration).coerceIn(0f, 1f)

                        val charWeights = FloatArray(word.text.length + if (hasTrailingSpace) 1 else 0) { idx ->
                            if (idx < word.text.length) charPacingWeight(word.text[idx]) else charPacingWeight(' ')
                        }
                        val totalWeight = charWeights.sum().coerceAtLeast(1f)
                        var accumWeight = 0f

                        word.text.forEachIndexed { charIdx, char ->
                            val startProg = accumWeight / totalWeight
                            accumWeight += charWeights[charIdx]
                            val endProg = accumWeight / totalWeight

                            val charStyle = when {
                                wordProgress >= endProg -> SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                                wordProgress >= startProg -> SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    shadow = Shadow(
                                        color = primaryColor,
                                        blurRadius = 14f,
                                        offset = Offset.Zero
                                    )
                                )
                                else -> SpanStyle(
                                    color = upcomingColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            withStyle(charStyle) {
                                append(char)
                            }
                        }

                        if (hasTrailingSpace) {
                            val spaceStartProg = accumWeight / totalWeight
                            val spaceSung = wordProgress >= spaceStartProg
                            withStyle(
                                SpanStyle(
                                    color = if (spaceSung) primaryColor else upcomingColor,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(" ")
                            }
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = contentModifier
    )
}

/** Pre-compensation offset to align human audio perception and hardware buffer latency. */
private const val LyricsLeadOffsetMs = 120L

/**
 * Cadence weighting for characters: commas and punctuation pauses linger longer, and inter-word
 * spaces are given dedicated duration so the visual transition feels natural rather than instant.
 */
private fun charPacingWeight(c: Char): Float = when (c) {
    ',' -> 2.6f
    ';', ':', '.' -> 2.2f
    ' ' -> 1.6f
    '-', '—' -> 1.4f
    else -> 1.0f
}
