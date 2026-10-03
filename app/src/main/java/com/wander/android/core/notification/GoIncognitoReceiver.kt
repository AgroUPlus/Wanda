package com.wander.android.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wander.android.data.repository.ListenerPresence
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Go incognito" button on a "someone is listening along" notification.
 *
 * A receiver, like [WorkActionReceiver], so the button acts without raising the app. Not exported:
 * only this app's own `PendingIntent` should be able to flip a privacy switch.
 */
@AndroidEntryPoint
class GoIncognitoReceiver : BroadcastReceiver() {

    @Inject internal lateinit var presence: ListenerPresence

    override fun onReceive(context: Context, intent: Intent) {
        // A server round trip, so the broadcast is kept alive until it lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                presence.goIncognito()
                    .onFailure { Log.i(TAG, "could not go incognito from the notification", it) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GoIncognitoReceiver"
    }
}
