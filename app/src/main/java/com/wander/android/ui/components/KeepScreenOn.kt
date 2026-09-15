package com.wander.android.ui.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the screen awake for as long as [keepAwake] is true, e.g. while synced lyrics are on
 * screen and there's nothing else — no touch, no scrolling — to keep the display timeout at bay.
 */
@Composable
internal fun KeepScreenOn(keepAwake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake) {
        if (keepAwake) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }
}
