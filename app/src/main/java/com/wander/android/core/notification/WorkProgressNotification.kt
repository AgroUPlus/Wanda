package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
        val notificationId: Int
    ) {
        LIBRARY_SYNC("wanda_library_sync", "Library sync", 4711),
        FINGERPRINT("wanda_fingerprint", "Measuring your library", 4713),
        DOWNLOAD("wanda_downloads", "Downloads", 4714)
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
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(if (total > 0) total else 0, done, total <= 0)
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
