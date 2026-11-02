package com.wander.android.ui.screens.social

import android.util.Log
import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.p2p.OffGridPairing

/**
 * What to say about a failed off-grid connection. The link never coming up is logged with its
 * reason (`WifiDirectLink` names a refused permission or an empty room) and told as one message
 * that points at the settings involved; the reason itself is English from the platform.
 */
@StringRes
internal fun describeLinkFailure(error: Throwable): Int = when {
    error !is OffGridPairing.PairingException -> {
        Log.w(TAG, "No direct link: ${error.loggableMessage()}")
        R.string.offgrid_error_no_link
    }
    error.failure is OffGridPairing.Failure.WrongPeer -> R.string.offgrid_error_wrong_peer
    error.failure is OffGridPairing.Failure.Unreadable -> R.string.offgrid_error_unreadable
    else -> R.string.offgrid_error_no_answer
}

private const val TAG = "OffGrid"
