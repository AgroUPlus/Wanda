package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.data.sources.agro.StatEntry
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.listSupporting
import com.wander.android.ui.theme.listTitle

/**
 * A top-N as a segmented list: rank, name and count on one line, and a thin bar under it.
 *
 * The bars are scaled against the top entry rather than the total, because the question a top five
 * answers is "how do these compare to each other". A name written "Title — Artist", as the statistics
 * sources join them, is split so the artist reads as supporting text.
 */
@Composable
internal fun RankedList(
    entries: List<StatEntry>,
    countLabel: @Composable (Long) -> String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (entries.isEmpty()) return
    val top = entries.maxOf { it.value }.coerceAtLeast(1L)
    Column(
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val (title, sub) = splitTitle(entry.name)
            RankRow(
                rank = index + 1,
                title = title,
                sub = sub,
                count = countLabel(entry.value),
                fraction = (entry.value.toFloat() / top).coerceIn(MIN_FRACTION, 1f),
                shape = segmentedShape(index, entries.size),
                compact = compact
            )
        }
    }
}

@Composable
internal fun RankRow(
    rank: Int,
    title: String,
    sub: String?,
    count: String,
    fraction: Float,
    shape: Shape,
    compact: Boolean = false
) {
    val first = rank == 1
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, shape)
            .padding(
                start = if (compact) 10.dp else 12.dp,
                end = 16.dp,
                top = if (compact) 10.dp else 12.dp,
                bottom = if (compact) 10.dp else 12.dp
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (compact) 32.dp else 40.dp)
                .background(if (first) colors.primary else colors.surfaceContainerHigh, CircleShape)
        ) {
            Text(
                text = rank.toString(),
                style = type.listTitle.copy(fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold),
                color = if (first) colors.onPrimary else colors.onSurfaceVariant
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        append(title)
                        if (sub != null) {
                            withStyle(SpanStyle(fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Normal, color = colors.onSurfaceVariant)) {
                                append(" · ")
                                append(sub)
                            }
                        }
                    },
                    style = type.listTitle.copy(fontSize = if (compact) 15.sp else 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = count,
                    style = type.listSupporting.copy(
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = if (first) colors.primary else MaterialTheme.extraColors.rankBarSecondary,
                trackColor = colors.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp)
            )
        }
    }
}

/**
 * "Title — Artist" into its two halves; a plain name has no supporting half. Split at the *last*
 * separator, as `StatsRepository` does: a title containing one is commoner than an artist that does.
 */
private fun splitTitle(name: String): Pair<String, String?> {
    val at = name.lastIndexOf(SEPARATOR)
    return if (at <= 0) name to null else name.substring(0, at) to name.substring(at + SEPARATOR.length)
}

private const val SEPARATOR = " — "

/** A bar shorter than this reads as a rendering fault rather than as a small number. */
private const val MIN_FRACTION = 0.02f
