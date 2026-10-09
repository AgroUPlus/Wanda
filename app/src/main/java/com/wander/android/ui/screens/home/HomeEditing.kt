package com.wander.android.ui.screens.home

import androidx.compose.runtime.compositionLocalOf

/**
 * True inside the Home customizer. Shelves are drawn exactly as on Home, so the few buttons they
 * carry read this to step aside while the page is being rearranged.
 */
internal val LocalHomeEditing = compositionLocalOf { false }
