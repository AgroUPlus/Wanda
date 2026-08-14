package com.wander.android.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * Splits the app Scaffold's content padding for screens with a fixed header above a scrolling
 * list.
 *
 * The whole padding cannot go on the list: its top inset would then sit *below* the header,
 * leaving the header itself under the status bar. Nor can it all go on the outer container, since
 * the list should scroll its content under the bottom bar rather than stop short of it.
 *
 * So the header takes [headerInset] and the list takes [listInset].
 */
@Composable
fun PaddingValues.headerInset(): PaddingValues =
    PaddingValues(top = calculateTopPadding())

/** The bottom inset only — lets list content scroll beneath the mini player and nav bar. */
@Composable
fun PaddingValues.listInset(): PaddingValues =
    PaddingValues(bottom = calculateBottomPadding())
