package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.wander.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "this is going to take a while" notification, for the workers that take a while.
 *
 * Two things are being bought here, and only one of them is the progress bar.
 *
 * The other is **being allowed to finish**. A plain `CoroutineWorker` is subject to the platform's
 * background limits: it can be deferred, and it is killed after roughly ten minutes. A worker that
 * calls `setForeground` is a foreground service for as long as it runs, which is the difference
 * between indexing a library and indexing the first few tracks of one over and over. That is why
 * this is not merely cosmetic — the fingerprint indexer was subject to exactly that ceiling.
 *
 * One channel per kind of work, rather than one shared "Wanda is busy" channel. They have very
 * different tolerances: a library sync is welcome, a fingerprint pass over a thousand tracks is
 * something a person may reasonably want silenced forever, and Android's channel settings are the
 * only place that choice can be expressed. A single channel would make muting one mute all.
 *
 * All three are `IMPORTANCE_LOW` and silent: these are chores the user asked for, not events to
 * interrupt them with.
 */
@Singleton
class WorkProgressNotification @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** A kind of long-running work, with the channel and notification slot that belong to it. */
    enum class Kind(
        val channelId: String,
        val channelName: String,
        val notificationId: Int,
        /** The `wanda://` host this notification opens, or null to just bring the app forward. */
        val deepLinkHost: String?
    ) {
        LIBRARY_SYNC("wanda_library_sync", "Library sync", 4711, deepLinkHost = null),
        FINGERPRINT("wanda_fingerprint", "Measuring your library", 4713, "fingerprints"),
        DOWNLOAD("wanda_downloads", "Downloads", 4714, deepLinkHost = null)
    }

    /**
     * A foreground notification for [kind], showing [done] of [total] when a total is known.
     *
     * `total <= 0` produces an indeterminate bar rather than no bar. Work whose size is not yet
     * known is still work in progress, and an empty notification reads as a stuck one.
     */
    fun foregroundInfo(
        kind: Kind,
        title: String,
        text: String,
        done: Int = 0,
        total: Int = 0
    ): ForegroundInfo {
        ensureChannel(kind)

        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setContentTitle(title)
            .setContentText(text)
            // The long line, because a track title plus a countdown does not fit on one and the
            // collapsed form truncates exactly the part that changes.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(if (total > 0) total else 0, done, total <= 0)
            .setContentIntent(openIntent(kind))
            // Pause and Cancel are different promises and both are worth offering. Pause means
            // "not now, and do not quietly start again" — the failure a bare cancel has, since the
            // next periodic trigger would restart it minutes later and look like the button not
            // working. Cancel means "stop this run", leaving the schedule intact.
            .addAction(0, "Pause", actionIntent(kind, WorkActionReceiver.ACTION_PAUSE))
            .addAction(0, "Cancel", actionIntent(kind, WorkActionReceiver.ACTION_CANCEL))
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                kind.notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(kind.notificationId, notification)
        }
    }

    /**
     * Where tapping the notification lands.
     *
     * The screen that explains the work, when there is one — a progress bar you cannot interrogate
     * is only half an answer, and "what is it actually doing" is the obvious next question. Where
     * there is no such screen the intent still exists, so the tap brings the app forward instead of
     * being inert, which reads as a broken notification.
     *
     * `IMMUTABLE` because nothing is meant to fill anything in, and it is required from API 31.
     */
    private fun openIntent(kind: Kind): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                kind.deepLinkHost?.let {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("wanda://$it")
                }
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: return null

        return PendingIntent.getActivity(
            context,
            kind.notificationId,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * A button on the notification, routed to [WorkActionReceiver].
     *
     * The request code mixes the kind with the action, because `PendingIntent` equality ignores
     * extras: sharing one code between Pause and Cancel would have `FLAG_UPDATE_CURRENT` rewrite
     * the first into the second, and both buttons would then do the same thing.
     */
    private fun actionIntent(kind: Kind, action: String): PendingIntent {
        val intent = Intent(context, WorkActionReceiver::class.java)
            .setAction(action)
            .putExtra(WorkActionReceiver.EXTRA_KIND, kind.name)

        return PendingIntent.getBroadcast(
            context,
            kind.notificationId * 10 + if (action == WorkActionReceiver.ACTION_PAUSE) 1 else 2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel(kind: Kind) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(kind.channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                kind.channelId,
                kind.channelName,
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
