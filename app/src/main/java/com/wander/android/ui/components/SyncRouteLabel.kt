package com.wander.android.ui.components

import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.data.sources.agro.SyncRoute

/** How a route is named on screen, by the sync offer card and sheet alike. */
@StringRes
internal fun SyncRoute.labelRes(): Int = when (this) {
    SyncRoute.DIRECT -> R.string.sync_route_direct
    SyncRoute.RELAY -> R.string.sync_route_relay
    SyncRoute.ARCHIVE -> R.string.sync_route_archive
}
