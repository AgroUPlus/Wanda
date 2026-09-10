package com.wander.android.data.repository

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroArtistRelease
import com.wander.android.data.sources.agro.AgroArtistSubscription
import com.wander.android.data.sources.agro.AgroArtistsApi
import com.wander.android.data.sources.ytmusic.InnerTubeClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Following an artist, in both places it can mean.
 *
 * Agro holds the subscription this app acts on: it is what `newReleases` is answered from and what
 * the release job polls. When the artist came from YouTube Music and the user is signed in, the
 * follow is mirrored onto that account too — following somebody here and then opening YouTube Music
 * to find nothing changed is worse than not having offered it.
 *
 * The mirror is deliberately best-effort and its failure is swallowed. It is a copy of a decision
 * that has already been recorded where it counts; a signed-out account, a rejected cookie or a bot
 * challenge must not make the button appear not to have worked.
 */
@Singleton
internal class ArtistSubscriptionRepository @Inject constructor(
    private val api: AgroArtistsApi,
    private val innerTube: InnerTubeClient,
    private val secureStorage: SecureStorage
) {
    suspend fun subscribed(): List<AgroArtistSubscription> =
        api.subscribed().getOrElse { emptyList() }

    suspend fun isSubscribed(artist: String): Boolean =
        api.isSubscribed(artist).getOrElse { false }

    /**
     * Follows [artist], carrying the YouTube Music channel when the caller knows it.
     *
     * [externalId] is stored on the artist row the first time any client supplies one, so a follow
     * made from a Navidrome page still gains the channel later when somebody follows the same
     * artist from a YouTube Music one.
     */
    suspend fun subscribe(artist: String, channelId: String? = null): Boolean {
        val subscription = api.subscribe(artist, channelId?.let { "ytm:$it" }).getOrNull()
            ?: return false
        mirrorToYouTube(subscription.externalId ?: channelId?.let { "ytm:$it" }, subscribed = true)
        return true
    }

    suspend fun unsubscribe(subscription: AgroArtistSubscription): Boolean {
        val removed = api.unsubscribe(subscription.artistId).getOrElse { false }
        if (removed) mirrorToYouTube(subscription.externalId, subscribed = false)
        return removed
    }

    /** Everything followed artists have published after [since], oldest first. */
    suspend fun newReleases(since: Long, limit: Int = 100): List<AgroArtistRelease> =
        api.newReleases(since, limit).getOrElse { emptyList() }

    private suspend fun mirrorToYouTube(externalId: String?, subscribed: Boolean) {
        val channel = externalId?.removePrefix("ytm:")?.takeIf { it.isNotBlank() } ?: return
        // Nothing to mirror onto until they have signed in; the Agro side already holds the follow.
        if (secureStorage.ytMusicAuthCookie.isBlank()) return
        runCatching { innerTube.setSubscribed(channel, subscribed) }
    }
}
