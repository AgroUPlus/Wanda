package com.wander.android.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.wander.android.R

/**
 * The look the app's social notifications share, so a drop, a friend request and a jam join read as
 * one family: the brand accent, one group per kind with a silent summary, and nothing private on
 * the lock screen.
 *
 * Lock-screen privacy is the system's choice, not this app's, once [NotificationCompat.VISIBILITY_PRIVATE] is set: the
 * user's "show sensitive content" setting decides whether the full notification or the public
 * version appears. The public version names no one — who is writing to you is itself the secret.
 */
internal class WandaNotificationStyle(
    private val context: Context,
    private val channelId: String,
    @param:DrawableRes private val smallIcon: Int,
    private val group: String,
    private val summaryId: Int,
    /** Built only the first time something is posted, as the channel's name is a resource. */
    private val channel: () -> NotificationChannel
) {
    private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

    /** A builder already dressed in the family look; [publicTitle] is all the lock screen gets. */
    fun builder(publicTitle: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setColor(context.getColor(R.color.notification_accent))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(group)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(publicTitle))

    /**
     * Posts [notification], and the group's summary with it.
     *
     * Posting without POST_NOTIFICATIONS throws on API 33+. The permission is declared, but the
     * user may have refused it, and a refused notification must not take down whatever posted it —
     * these are posted from the sync socket's frame handler, which still has the rest of the frame
     * to deal with. Only the exception's class is logged: its message can carry the tag, and a tag
     * here holds a username.
     */
    fun post(tag: String?, id: Int, notification: Notification) {
        val manager = manager ?: return
        if (manager.getNotificationChannel(channelId) == null) manager.createNotificationChannel(channel())
        try {
            manager.notify(tag, id, notification)
            manager.notify(summaryId, summary())
        } catch (e: SecurityException) {
            Log.i(TAG, "could not post a notification in $group: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Takes one down, and the summary too once it stands for nothing. A summary left behind on its
     * own is an empty row in the shade that opens nothing.
     */
    fun cancel(tag: String?, id: Int) {
        val manager = manager ?: return
        manager.cancel(tag, id)
        val children = manager.activeNotifications
            .count { it.notification.group == group && it.id != summaryId }
        if (children == 0) manager.cancel(summaryId)
    }

    /** Takes down the whole group, summary included. */
    fun cancelAll() {
        val manager = manager ?: return
        manager.activeNotifications
            .filter { it.notification.group == group }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** What is in the shade under [tag] and [id] right now, if anything. */
    fun shown(tag: String, id: Int): Notification? =
        manager?.activeNotifications?.firstOrNull { it.tag == tag && it.id == id }?.notification

    /** Bundles the group; the children alert, so the summary itself never makes a sound. */
    private fun summary(): Notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(smallIcon)
        .setColor(context.getColor(R.color.notification_accent))
        .setGroup(group)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setSilent(true)
        .setAutoCancel(true)
        .build()

    private fun publicVersion(title: String): Notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(smallIcon)
        .setColor(context.getColor(R.color.notification_accent))
        .setContentTitle(title)
        .build()

    private companion object {
        const val TAG = "WandaNotificationStyle"
    }
}
