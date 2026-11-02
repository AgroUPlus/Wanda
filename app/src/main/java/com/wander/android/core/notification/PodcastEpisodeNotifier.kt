package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wander.android.MainActivity
import com.wander.android.R
import com.wander.android.data.repository.NewEpisode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says that a subscribed show published something, after a background sync pass found it.
 *
 * Its own channel, so new episodes can be silenced without silencing friends or releases. One
 * notification per pass, replaced by the next: a single episode is named, several are counted.
 * Nothing is posted while notifications are refused (POST_NOTIFICATIONS on API 33+, or switched
 * off for the app), since posting then throws.
 */
@Singleton
internal class PodcastEpisodeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun notifyNewEpisodes(episodes: List<NewEpisode>) {
        if (episodes.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val single = episodes.singleOrNull()
        val title = if (single != null) {
            context.getString(R.string.notif_podcast_new_episode, single.showTitle)
        } else {
            context.resources.getQuantityString(R.plurals.notif_podcast_new_episodes, episodes.size, episodes.size)
        }
        val body = single?.title ?: episodes.map { it.showTitle }.distinct().joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try {
            manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Refused between the check above and now; the episodes are in the Inbox regardless.
            Log.w(TAG, "New-episode notification refused: ${e.javaClass.simpleName}")
        }
    }

    /** Explicit, naming `MainActivity`; `FLAG_IMMUTABLE` because nothing receiving it should rewrite it. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_podcast_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "wanda_new_episodes"
        /** Tagged, so the id cannot land on another notifier's numbered range. */
        const val NOTIFICATION_TAG = "podcast_new_episodes"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 4501
        const val TAG = "PodcastEpisodeNotifier"
    }
}
