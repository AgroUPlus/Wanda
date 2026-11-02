package com.wander.android.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable

/**
 * The insets the shell's Scaffold hands every screen as its content padding.
 *
 * The Scaffold's default covers the system bars only. In landscape a display cutout sits on a
 * *side* of the window, and without it here every screen's start or end edge ran under the notch.
 * Only the horizontal cutout edges are added: the top is the status bar's already, and the bottom
 * belongs to the dock metrics — see `calculateDockMetrics`.
 */
@Composable
internal fun shellContentInsets(): WindowInsets =
    ScaffoldDefaults.contentWindowInsets
        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
