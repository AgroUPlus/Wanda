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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says when somebody joins or leaves your jam, or starts listening along with you.
 *
 * Like [FriendNotifier], these come off the sync socket, which is only open while Wanda is on
 * screen — so this reports what happened while you were using the app, not a background push.
 *
 * Someone listening along hears everything you play. That notification carries a way out:
 * "Go incognito" ends every listen-along session following this account, server-side.
 *
 * Dressed like the friend notifications ([WandaNotificationStyle]): the accent, one group with a
 * silent summary, and only "Listening together" on the lock screen — who is listening to you is
 * as private as who writes to you.
 */
@Singleton
internal class ListeningTogetherNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val style = WandaNotificationStyle(
        context = context,
        channelId = CHANNEL_ID,
        smallIcon = R.drawable.ic_stat_together,
        group = GROUP,
        summaryId = SUMMARY_ID
    ) {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.together_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.together_channel_description) }
    }

    fun listenerJoined(username: String) = post(
        tag = listenerTag(username),
        id = LISTENER_ID,
        builder = base(context.getString(R.string.together_listener_joined_title, username))
            .setContentText(context.getString(R.string.together_listener_joined_body))
            .setContentIntent(openIntent(PRIVACY_URI, PRIVACY_REQUEST_CODE))
            .addAction(
                R.drawable.ic_stat_together,
                context.getString(R.string.together_go_incognito),
                incognitoIntent()
            )
    )

    /** Someone stopped following: their "is listening along" is no longer true, so it goes. */
    fun listenerLeft(username: String) = style.cancel(listenerTag(username), LISTENER_ID)

    /** Clears every listener notification — after going incognito, none of them is true any more. */
    fun clearListeners(usernames: Collection<String>) = usernames.forEach(::listenerLeft)

    fun jamMemberJoined(username: String, roomSize: Int) =
        postJam(username, R.string.together_jam_joined_title, roomSize)

    fun jamMemberLeft(username: String, roomSize: Int) =
        postJam(username, R.string.together_jam_left_title, roomSize)

    /** One per person: a join followed by a leave replaces rather than stacks. */
    private fun postJam(username: String, title: Int, roomSize: Int) = post(
        tag = "jam:${username.lowercase()}",
        id = JAM_ID,
        builder = base(context.getString(title, username))
            .setContentText(
                context.resources.getQuantityString(R.plurals.together_jam_room_size, roomSize, roomSize)
            )
            .setContentIntent(openIntent(JAM_URI, JAM_REQUEST_CODE))
            .setTimeoutAfter(JAM_TIMEOUT_MS)
    )

    private fun base(title: String): NotificationCompat.Builder =
        style.builder(context.getString(R.string.together_channel_name)).setContentTitle(title)

    // A refused POST_NOTIFICATIONS is handled by the style: the frame that brought this news still
    // has the rest of its work to do.
    private fun post(tag: String, id: Int, builder: NotificationCompat.Builder) = style.post(tag, id, builder.build())

    private fun listenerTag(username: String) = "listener:${username.lowercase()}"

    private fun openIntent(uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri.toUri()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun incognitoIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        INCOGNITO_REQUEST_CODE,
        Intent(context, GoIncognitoReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val CHANNEL_ID = "wanda_together"
        const val GROUP = "wanda_together"
        // Tagged by person, so these ids only need to be apart from each other and the summary's.
        const val LISTENER_ID = 1
        const val JAM_ID = 2
        const val SUMMARY_ID = 45_999
        const val JAM_REQUEST_CODE = 4401
        const val PRIVACY_REQUEST_CODE = 4402
        const val INCOGNITO_REQUEST_CODE = 4403
        const val JAM_URI = "wanda://jam-room"
        const val PRIVACY_URI = "wanda://privacy"
        /** Jam comings and goings are news for a while, not a record to keep in the shade. */
        const val JAM_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
