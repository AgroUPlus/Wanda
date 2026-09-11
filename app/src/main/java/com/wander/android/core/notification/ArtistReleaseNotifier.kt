package com.wander.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
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
    /**
     * [artworkFor] answers with a cover for a release, or null when nothing local matches it.
     *
     * Passed in rather than looked up here: which cover belongs to a release is a repository's
     * question, and a notifier that could answer it would need the library to do so.
     */
    suspend fun notifyReleases(
        releases: List<AgroArtistRelease>,
        artworkFor: suspend (AgroArtistRelease) -> String? = { null }
    ) {
        if (releases.isEmpty()) return
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (releases.size <= INDIVIDUAL_LIMIT) {
            releases.forEach { release ->
                val cover = runCatching { artworkFor(release) }.getOrNull()?.let { loadCover(it) }
                // Wrapped, as `FriendNotifier` wraps its own: on API 33+ a refused
                // POST_NOTIFICATIONS makes this throw, and the caller has already moved its
                // watermark — so an exception here would lose the batch *and* fail the worker.
                runCatching {
                    manager.notify(
                        RELEASE_ID_BASE + release.recordingId.hashCode(),
                        build(
                            title = release.artist,
                            body = listOfNotNull(release.title, release.album)
                                .firstOrNull()
                                ?.let { "New: $it" }
                                ?: "Something new is out",
                            cover = cover
                        )
                    )
                }
            }
            return
        }

        val artists = releases.map { it.artist }.distinct()
        // No cover on the summary. It stands for several records, and picking one of their sleeves
        // to represent the rest would say something the notification does not mean.
        runCatching {
            manager.notify(
                SUMMARY_ID,
                build(
                    title = "${releases.size} new releases",
                    body = when (artists.size) {
                        1 -> "From ${artists.first()}"
                        2 -> "From ${artists[0]} and ${artists[1]}"
                        else -> "From ${artists[0]}, ${artists[1]} and ${artists.size - 2} more"
                    },
                    cover = null
                )
            )
        }
    }

    /**
     * The cover as a bitmap, or null if it cannot be had.
     *
     * The one imperative Coil call in the app — everywhere else artwork is a composable, and a
     * notification has no composition to hang one in. Decoded to a fixed edge rather than the
     * bucket ladder the UI uses: the shade scales the large icon itself, and the ladder's helpers
     * live in the UI package, which a notifier has no business reaching into.
     *
     * Null on any failure, by design. A release with no sleeve is still worth announcing, and a
     * notification that never arrives because an image 404'd would be the worse bug.
     */
    private suspend fun loadCover(url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(CoverPx, CoverPx))
            .precision(Precision.INEXACT)
            .build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()

    private fun build(title: String, body: String, cover: Bitmap?) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .apply { cover?.let { setLargeIcon(it) } }
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

        /** Comfortably above what the shade draws a large icon at, on any density. */
        const val CoverPx = 256
    }
}
