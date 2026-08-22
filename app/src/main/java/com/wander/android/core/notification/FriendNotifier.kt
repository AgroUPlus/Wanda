package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import com.wander.android.MainActivity
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.data.sources.agro.FriendEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says out loud when somebody asks to be your friend, or answers when you asked.
 *
 * These arrive on the sync socket, which is open only while the app is on screen — the battery
 * rule this app is built around. So this is a notification about something that happened *while
 * you were using Wanda*, not a push: a request sent while the app is closed is seen on the Friends
 * screen at next launch instead. Real background delivery needs a push service, which is a
 * dependency this app does not otherwise have.
 */
@Singleton
internal class FriendNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun notify(event: FriendEvent) {
        val (title, body) = when (event) {
            is FriendEvent.Requested ->
                "Friend request" to "${event.from} wants to be your friend"
            is FriendEvent.Accepted ->
                "Friend request accepted" to "${event.by} has accepted your friend request!"
            // A decline is deliberately silent. Being turned down is not something to interrupt
            // someone about, and the request simply disappearing from the list says it well enough.
            is FriendEvent.Declined -> return
        }

        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setAutoCancel(true)
            .build()

        // Posting without POST_NOTIFICATIONS throws on API 33+. The permission is declared, but the
        // user may have refused it, and a refused notification must not take the socket down with
        // it — everything else that frame triggers still has to happen.
        runCatching { manager.notify(notificationId(event), notification) }
            .onFailure { android.util.Log.i(TAG, "could not post the friend notification") }
    }

    /**
     * Says out loud that a friend handed you a song.
     *
     * Shares the Friends channel rather than opening its own: from a user's point of view this is
     * the same kind of interruption as a friend request, and a second channel would mean two
     * switches to find in settings for one idea.
     *
     * Keyed on the drop's id rather than on the sender, unlike the friend events above. Two
     * requests from the same person are the same news and should replace each other; two songs from
     * the same person are two songs, and collapsing them would silently hide one.
     */
    fun notifyDrop(drop: AgroDrop) {
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val body = buildString {
            append(drop.trackTitle)
            if (drop.artistName.isNotBlank()) append(" — ").append(drop.artistName)
            drop.note?.takeIf { it.isNotBlank() }?.let { append("\n\u201C").append(it).append("\u201D") }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${drop.fromUser} sent you a track")
            .setContentText(body)
            // The note is the reason they sent it, and it is the part that does not fit on one line.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setAutoCancel(true)
            // Without this the notification was inert: tapping it did nothing at all, which reads
            // as the app being broken rather than as the notification being informational.
            .setContentIntent(inboxIntent())
            .build()

        runCatching { manager.notify(DROP_ID_BASE + drop.id.hashCode(), notification) }
            .onFailure { android.util.Log.i(TAG, "could not post the drop notification") }
    }

    /** One notification per person per kind, so repeated frames replace rather than stack up. */
    private fun notificationId(event: FriendEvent): Int = when (event) {
        is FriendEvent.Requested -> REQUEST_ID_BASE + event.from.hashCode()
        is FriendEvent.Accepted -> ACCEPT_ID_BASE + event.by.hashCode()
        is FriendEvent.Declined -> 0
    }

    /**
     * Opens the app on the inbox.
     *
     * An *explicit* intent — it names `MainActivity` directly — so it does not depend on a manifest
     * filter matching `wanda://inbox`. `FLAG_IMMUTABLE` because nothing receiving this is meant to
     * rewrite it, and API 34 refuses a PendingIntent that declares neither.
     */
    private fun inboxIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = INBOX_URI.toUri()
            // Comes back to the existing task rather than stacking a second copy of the app behind
            // whatever the user was already doing.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            INBOX_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Friends",
                // Someone waiting on an answer is worth a glance, unlike the sync chores.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val TAG = "FriendNotifier"
        const val CHANNEL_ID = "wanda_friends"
        const val REQUEST_ID_BASE = 41_000
        const val ACCEPT_ID_BASE = 42_000
        const val DROP_ID_BASE = 43_000
        const val INBOX_REQUEST_CODE = 4301
        const val INBOX_URI = "wanda://inbox"
    }
}
