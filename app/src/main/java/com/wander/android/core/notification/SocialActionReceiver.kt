package com.wander.android.core.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.ListenAlongResolver
import com.wander.android.data.repository.SocialRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The buttons on the social notifications: Play, Love it and Mark as read on a drop thread, Accept
 * and Decline on a friend request.
 *
 * A receiver, like [GoIncognitoReceiver], so each acts without raising the app. Not exported: only
 * this app's own PendingIntents should be able to answer a friend request on the user's behalf.
 *
 * There is no Reply. Agro has no way to answer a drop with words: a drop is a track, and the only
 * thing that can go back on one is a reaction (`reactToDrop`), so the reply slot is a reaction.
 */
@AndroidEntryPoint
class SocialActionReceiver : BroadcastReceiver() {
    @Inject internal lateinit var drops: DropsRepository
    @Inject internal lateinit var social: SocialRepository
    @Inject internal lateinit var resolver: ListenAlongResolver
    @Inject internal lateinit var player: PlayerConnection
    @Inject internal lateinit var dropNotifier: DropNotifier
    @Inject internal lateinit var friendNotifier: FriendNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: return
        val dropId = intent.getStringExtra(EXTRA_DROP_ID)
        // Every action is a round trip, so the broadcast is kept alive until it lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) {
                    when (intent.action) {
                        ACTION_PLAY -> dropId?.let { play(context, username, it) }
                        ACTION_LOVE -> dropId?.let { love(context, username, it) }
                        ACTION_MARK_READ -> markRead(username)
                        ACTION_ACCEPT -> answer(context, username, accept = true)
                        ACTION_DECLINE -> answer(context, username, accept = false)
                    }
                } ?: Log.i(TAG, "a notification action ran out of time")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Plays the newest drop in the thread, found the way the inbox finds one — your library first,
     * then YouTube Music — and through `PlayerConnection.play`, so in a jam it is proposed to the
     * room instead, as every other play is.
     */
    private suspend fun play(context: Context, username: String, dropId: String) {
        val drop = drops.find(dropId) ?: return
        if (drop.isUnread) drops.markRead(drop.id)
        val resolved = resolver.resolve(drop.trackTitle, drop.artistName)
        if (resolved == null) {
            toast(context, context.getString(R.string.notif_drop_not_found, drop.trackTitle))
            return
        }
        if (!player.awaitConnected(CONNECT_MILLIS)) {
            toast(context, context.getString(R.string.notif_drop_play_failed))
            return
        }
        // A MediaController may only be touched on the main thread.
        withContext(Dispatchers.Main.immediate) { player.play(listOf(resolved.track)) }
        dropNotifier.dismiss(username)
    }

    private suspend fun love(context: Context, username: String, dropId: String) {
        val loved = drops.react(dropId, DropNotifier.LOVE).getOrDefault(false)
        if (loved) dropNotifier.showReaction(username) else toast(context, R.string.notif_drop_love_failed)
    }

    /**
     * Read locally first, as the inbox does, so the badge agrees at once; a server call that failed
     * is put right by the next refresh, which is why the notification goes regardless.
     */
    private suspend fun markRead(username: String) {
        drops.markThreadRead(username)
        dropNotifier.dismiss(username)
    }

    /** Declining is `removeFriend`, the same call the Decline button on a profile makes. */
    private suspend fun answer(context: Context, username: String, accept: Boolean) {
        val result = if (accept) social.accept(username) else social.remove(username)
        if (result.getOrDefault(false)) {
            friendNotifier.dismissRequest(username)
        } else {
            toast(context, R.string.notif_friend_answer_failed)
        }
    }

    private suspend fun toast(context: Context, @StringRes text: Int) = toast(context, context.getString(text))

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    internal companion object {
        const val ACTION_PLAY = "com.wander.android.notification.PLAY_DROP"
        const val ACTION_LOVE = "com.wander.android.notification.LOVE_DROP"
        const val ACTION_MARK_READ = "com.wander.android.notification.MARK_THREAD_READ"
        const val ACTION_ACCEPT = "com.wander.android.notification.ACCEPT_FRIEND"
        const val ACTION_DECLINE = "com.wander.android.notification.DECLINE_FRIEND"
        private const val EXTRA_USERNAME = "com.wander.android.notification.USERNAME"
        private const val EXTRA_DROP_ID = "com.wander.android.notification.DROP_ID"
        private const val TAG = "SocialActionReceiver"

        // A receiver gets about ten seconds; finding a track on YouTube Music can take most of it.
        private const val BUDGET_MILLIS = 9_000L
        private const val CONNECT_MILLIS = 3_000L

        /**
         * The PendingIntent behind one button. Immutable: nothing these buttons do takes input from
         * the system, and API 34 refuses one that declares neither.
         */
        fun pendingIntent(context: Context, action: String, username: String, dropId: String? = null): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SocialActionReceiver::class.java)
                    .setAction(action)
                    .setData(SocialNotificationKeys.actionData(action, username).toUri())
                    .putExtra(EXTRA_USERNAME, username)
                    .apply { if (dropId != null) putExtra(EXTRA_DROP_ID, dropId) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
