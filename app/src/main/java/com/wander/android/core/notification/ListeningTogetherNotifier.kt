package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
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
 */
@Singleton
internal class ListeningTogetherNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun listenerJoined(username: String) = post(
        id = LISTENER_ID_BASE + username.lowercase().hashCode(),
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
    fun listenerLeft(username: String) {
        manager()?.cancel(LISTENER_ID_BASE + username.lowercase().hashCode())
    }

    /** Clears every listener notification — after going incognito, none of them is true any more. */
    fun clearListeners(usernames: Collection<String>) = usernames.forEach(::listenerLeft)

    fun jamMemberJoined(username: String, roomSize: Int) =
        postJam(username, R.string.together_jam_joined_title, roomSize)

    fun jamMemberLeft(username: String, roomSize: Int) =
        postJam(username, R.string.together_jam_left_title, roomSize)

    /** One per person: a join followed by a leave replaces rather than stacks. */
    private fun postJam(username: String, title: Int, roomSize: Int) = post(
        id = JAM_ID_BASE + username.lowercase().hashCode(),
        builder = base(context.getString(title, username))
            .setContentText(
                context.resources.getQuantityString(R.plurals.together_jam_room_size, roomSize, roomSize)
            )
            .setContentIntent(openIntent(JAM_URI, JAM_REQUEST_CODE))
            .setTimeoutAfter(JAM_TIMEOUT_MS)
    )

    private fun base(title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_together)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setColor(ACCENT)
            .setGroup(GROUP)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        ensureChannel()
        // Refused POST_NOTIFICATIONS throws on API 33+; the frame that brought this news still has
        // the rest of its work to do, so a refusal is logged rather than allowed to propagate.
        try {
            manager()?.notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.i(TAG, "could not post the listening-together notification", e)
        }
    }

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

    private fun manager(): NotificationManager? = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        val manager = manager() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.together_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.together_channel_description) }
        )
    }

    private companion object {
        const val TAG = "ListeningTogether"
        const val CHANNEL_ID = "wanda_together"
        const val GROUP = "wanda_together"
        /** The brand's tertiary tone, so these read as the app's own rather than as system chrome. */
        const val ACCENT = 0xFF7D5260.toInt()
        const val LISTENER_ID_BASE = 44_000
        const val JAM_ID_BASE = 45_000
        const val JAM_REQUEST_CODE = 4401
        const val PRIVACY_REQUEST_CODE = 4402
        const val INCOGNITO_REQUEST_CODE = 4403
        const val JAM_URI = "wanda://jam-room"
        const val PRIVACY_URI = "wanda://privacy"
        /** Jam comings and goings are news for a while, not a record to keep in the shade. */
        const val JAM_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
