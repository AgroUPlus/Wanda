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
import com.wander.android.data.sources.agro.AgroArtistRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Says that somebody the user follows has put something out.
 *
 * Its own channel, separate from `wanda_friends`: this is the one notification in the app the user
 * asked for by name, artist by artist, and it should be silenceable without also silencing a friend
 * request. `IMPORTANCE_DEFAULT` because it is the point of having subscribed — a release that
 * arrives silently in the shade has not told anyone anything.
 *
 * More than a handful at once is summarised rather than listed. Following a prolific artist, or
 * coming back after a fortnight away, otherwise means a screenful of near-identical rows to swipe
 * through, and the interesting fact by then is the number.
 */
@Singleton
internal class ArtistReleaseNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun notifyReleases(releases: List<AgroArtistRelease>) {
        if (releases.isEmpty()) return
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (releases.size <= INDIVIDUAL_LIMIT) {
            releases.forEach { release ->
                manager.notify(
                    RELEASE_ID_BASE + release.recordingId.hashCode(),
                    build(
                        title = release.artist,
                        body = listOfNotNull(release.title, release.album)
                            .firstOrNull()
                            ?.let { "New: $it" }
                            ?: "Something new is out"
                    )
                )
            }
            return
        }

        val artists = releases.map { it.artist }.distinct()
        manager.notify(
            SUMMARY_ID,
            build(
                title = "${releases.size} new releases",
                body = when (artists.size) {
                    1 -> "From ${artists.first()}"
                    2 -> "From ${artists[0]} and ${artists[1]}"
                    else -> "From ${artists[0]}, ${artists[1]} and ${artists.size - 2} more"
                }
            )
        )
    }

    private fun build(title: String, body: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .build()

    /**
     * Opens the app on Activity, where the release is also listed.
     *
     * Explicit, naming `MainActivity`, so it does not depend on a manifest filter; `FLAG_IMMUTABLE`
     * because nothing receiving it should rewrite it, and API 34 refuses a PendingIntent declaring
     * neither.
     */
    private fun activityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = ACTIVITY_URI.toUri()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            ACTIVITY_REQUEST_CODE,
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
                "New releases",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "wanda_new_releases"
        const val RELEASE_ID_BASE = 44_000
        const val SUMMARY_ID = 44_999
        const val ACTIVITY_REQUEST_CODE = 4401
        const val ACTIVITY_URI = "wanda://inbox"

        /** Past this, one summary. See the note on the class. */
        const val INDIVIDUAL_LIMIT = 3
    }
}
