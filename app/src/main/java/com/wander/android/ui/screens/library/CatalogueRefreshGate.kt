package com.wander.android.ui.screens.library

import android.os.SystemClock

/**
 * How often visiting the library rescans the device and re-reads every source's albums on its own.
 *
 * Coming back to the library is not news for either, and on a large library both are slow. Held
 * for the process, so leaving and coming back does not reset it. A pull always goes through.
 */
internal object CatalogueRefreshGate {
    private const val INTERVAL_MS = 5 * 60 * 1000L

    @Volatile
    private var last = 0L

    @Synchronized
    fun due(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (last != 0L && now - last < INTERVAL_MS) return false
        last = now
        return true
    }
}
