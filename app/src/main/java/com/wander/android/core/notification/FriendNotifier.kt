package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.wander.android.MainActivity
import com.wander.android.R
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.data.sources.agro.FriendEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says out loud when somebody asks to be your friend, answers when you asked, or asks you into a
 * blend. Songs friends send are [DropNotifier]'s; both share this one channel and one group, since
 * to the user they are the same kind of interruption, and a second channel would mean two switches
 * to find in settings for one idea.
 *
 * These arrive on the sync socket, which is open only while the app is on screen — the battery
 * rule this app is built around. So this is a notification about something that happened *while
 * you were using Wanda*, not a push: a request sent while the app is closed is seen on the Friends
 * screen at next launch instead. Real background delivery needs a push service, which is a
 * dependency this app does not otherwise have.
 *
 * The person is the picture: their face as the large icon, drawn when they have no photo.
 */
@Singleton
internal class FriendNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val avatars: NotificationAvatars
) {
    // Its own scope: the socket is collected with `collectLatest`, and the next frame would
    // otherwise cancel a notification still waiting on a face.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val style = style(context)

    fun notify(event: FriendEvent) {
        when (event) {
            is FriendEvent.Requested -> scope.launch { request(event.from) }
            is FriendEvent.Accepted -> scope.launch { accepted(event.by) }
            // A decline is deliberately silent. Being turned down is not something to interrupt
            // someone about, and the request simply disappearing from the list says it well enough.
            is FriendEvent.Declined -> Unit
        }
    }

    /** One per person, so a repeated frame replaces rather than stacks; answerable from the shade. */
    private suspend fun request(username: String) {
        val face = avatars.face(username)
        val notification = style.builder(context.getString(R.string.friend_request_title))
            .setContentTitle(context.getString(R.string.friend_request_title))
            .setContentText(context.getString(R.string.friend_request_body, face.name))
            .setLargeIcon(face.avatar)
            .setContentIntent(open(SocialNotificationKeys.profileLink(username)))
            .addAction(action(R.string.notif_action_accept, SocialActionReceiver.ACTION_ACCEPT, username))
            .addAction(action(R.string.notif_action_decline, SocialActionReceiver.ACTION_DECLINE, username))
            .build()
        style.post(SocialNotificationKeys.requestTag(username), SocialNotificationKeys.REQUEST_ID, notification)
    }

    private suspend fun accepted(username: String) {
        val face = avatars.face(username)
        val notification = style.builder(context.getString(R.string.friend_accepted_title))
            .setContentTitle(context.getString(R.string.friend_accepted_title))
            .setContentText(context.getString(R.string.friend_accepted_body, face.name))
            .setLargeIcon(face.avatar)
            .setContentIntent(open(SocialNotificationKeys.profileLink(username)))
            .build()
        style.post(SocialNotificationKeys.acceptTag(username), SocialNotificationKeys.ACCEPT_ID, notification)
    }

    /** The request was answered, here or from the shade; it no longer asks anything. */
    fun dismissRequest(username: String) =
        style.cancel(SocialNotificationKeys.requestTag(username), SocialNotificationKeys.REQUEST_ID)

    /**
     * Says out loud that someone asked you into a blend. Opens on Activity, where it can be
     * answered. Keyed on the blend, so asking again replaces rather than stacks.
     */
    fun notifyBlendInvite(invite: BlendInfo) {
        scope.launch {
            val face = avatars.face(invite.createdBy)
            val notification = style.builder(context.getString(R.string.notif_blend_invite_title))
                .setContentTitle(context.getString(R.string.notif_blend_invite_title))
                .setContentText(context.getString(R.string.blend_invite_from, invite.createdBy, invite.title))
                .setLargeIcon(face.avatar)
                .setContentIntent(open(INBOX_URI))
                .build()
            val tag = SocialNotificationKeys.blendTag(invite.playlistId)
            style.post(tag, SocialNotificationKeys.BLEND_ID, notification)
        }
    }

    private fun action(label: Int, action: String, username: String) = NotificationCompat.Action.Builder(
        R.drawable.ic_stat_sync,
        context.getString(label),
        SocialActionReceiver.pendingIntent(context, action, username)
    ).setShowsUserInterface(false).build()

    /**
     * Opens the app on [link].
     *
     * An *explicit* intent — it names `MainActivity` directly — so it does not depend on a manifest
     * filter matching the link. `FLAG_IMMUTABLE` because nothing receiving this is meant to rewrite
     * it, and API 34 refuses a PendingIntent that declares neither. The link is the intent's data,
     * which keeps one person's PendingIntent apart from the next person's.
     */
    private fun open(link: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = link.toUri()
            // Comes back to the existing task rather than stacking a second copy of the app behind
            // whatever the user was already doing.
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "wanda_friends"
        private const val INBOX_URI = "wanda://inbox"

        /** The Friends channel's look, shared with [DropNotifier] so both post into one group. */
        fun style(context: Context) = WandaNotificationStyle(
            context = context,
            channelId = CHANNEL_ID,
            smallIcon = R.drawable.ic_stat_sync,
            group = SocialNotificationKeys.GROUP,
            summaryId = SocialNotificationKeys.SUMMARY_ID
        ) {
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.friends_channel_name),
                // Someone waiting on an answer is worth a glance, unlike the sync chores.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        }
    }
}
