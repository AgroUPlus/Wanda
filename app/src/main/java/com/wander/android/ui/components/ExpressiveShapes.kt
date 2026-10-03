package com.wander.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * People are cookies and places are not. Every avatar on the social screens is the nine-lobed
 * cookie, so a face reads as a face wherever it turns up; the four-lobed one is kept for icons that
 * stand for something other than a person, and the sunny shape for a figure worth celebrating.
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val PersonShape: Shape
    @Composable get() = MaterialShapes.Cookie9Sided.toShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val EmblemShape: Shape
    @Composable get() = MaterialShapes.Cookie4Sided.toShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val SunnyShape: Shape
    @Composable get() = MaterialShapes.Sunny.toShape()

/**
 * The corners of one item in a segmented (connected) list.
 *
 * The outer corners of the group are round and the seams between items are tight, so a run of rows
 * reads as one card cut into strips rather than as a stack of separate cards.
 */
internal fun segmentedShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The gap between the strips of a segmented list. */
internal val SegmentGap = 2.dp
