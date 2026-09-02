package com.wander.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wander.android.core.notification.WorkProgressNotification.Kind
import com.wander.android.core.work.WorkControls
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The Pause and Cancel buttons on a progress notification.
 *
 * A receiver rather than an activity, because neither button has anything to show. Routing them
 * through the app would raise a screen over whatever the user was doing to perform an action they
 * asked for from outside it — the notification is the interface here, and it stays the interface.
 *
 * Not exported: the only sender is this app's own `PendingIntent`. An exported receiver would let
 * any installed app stop the user's library sync, which is a small thing to be able to do and no
 * reason for anyone else to be able to do it.
 */
@AndroidEntryPoint
class WorkActionReceiver : BroadcastReceiver() {

    @Inject lateinit var controls: WorkControls

    override fun onReceive(context: Context, intent: Intent) {
        // Unknown values are ignored rather than defaulted. A malformed intent naming no job is not
        // a reason to pause an arbitrary one.
        val kind = intent.getStringExtra(EXTRA_KIND)
            ?.let { name -> Kind.entries.firstOrNull { it.name == name } }
            ?: return

        when (intent.action) {
            ACTION_PAUSE -> controls.pause(kind)
            ACTION_CANCEL -> controls.cancel(kind)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.wander.android.action.PAUSE_WORK"
        const val ACTION_CANCEL = "com.wander.android.action.CANCEL_WORK"
        const val EXTRA_KIND = "kind"
    }
}
