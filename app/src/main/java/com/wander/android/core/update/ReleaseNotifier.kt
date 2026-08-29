package com.wander.android.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.wander.android.R
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says, once, that a new version has been published.
 *
 * Once per *version*, not once per check: the check runs on a schedule, and re-announcing the same
 * release every day is how a user turns notifications off altogether. The last announced version
 * is remembered, so a release is mentioned when it appears and then left alone.
 *
 * Tapping opens the release page in a browser rather than downloading anything. The app does not
 * install its own updates, and a notification that silently fetched an APK would be doing
 * something the user never agreed to.
 */
@Singleton
class ReleaseNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {

    fun notifyIfNew(update: UpdateCheckResult.UpdateAvailable) {
        if (secureStorage.lastNotifiedRelease == update.version) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("Wanda ${update.version} is out")
            .setContentText("Tap to see what changed.")
            .setContentIntent(releaseIntent(update.releaseUrl))
            .setAutoCancel(true)
            // Low: a new version is news, not an interruption. Nothing is waiting on the user.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onSuccess { secureStorage.lastNotifiedRelease = update.version }
            .onFailure {
                // Notification permission refused, most likely. Not worth retrying and not worth
                // failing the check over — but the version is deliberately *not* recorded, so it
                // is announced properly if permission is granted later.
                Log.w(TAG, "Could not post the release notification: ${it.message}")
            }
    }

    private fun releaseIntent(url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
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
                "Updates",
                // Its own channel so it can be silenced without silencing anything that matters.
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private companion object {
        const val TAG = "ReleaseNotifier"
        const val CHANNEL_ID = "wanda_releases"
        const val NOTIFICATION_ID = 43_001
        const val REQUEST_CODE = 43_002
    }
}
