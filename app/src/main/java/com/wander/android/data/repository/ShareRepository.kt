package com.wander.android.data.repository

import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Public links to tracks, minted by the backend that hosts them.
 *
 * The link is the server's own — Navidrome's `createShare` returns a URL on whatever public
 * address the server is published at — so a link shared from Wanda is the same link Wander's
 * share overlay produces. Nothing is constructed here: a URL this app invented would not play.
 *
 * The finished link is published on [links] rather than returned. Turning a URL into a share
 * sheet needs an Activity, which a ViewModel has no business holding, so `WanderApp` collects
 * this in one place the same way it already collects [MusicRepository.writeErrors] — and a
 * shared track therefore behaves identically from Home, Library, Search or the player.
 */
@Singleton
class ShareRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareLinkRewriter: ShareLinkRewriter
) {

    private val _links = MutableSharedFlow<ShareLink>(extraBufferCapacity = 1)
    val links: SharedFlow<ShareLink> = _links.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Whether [track]'s backend can publish a link at all — decides if the action is offered. */
    fun canShare(track: UnifiedTrack): Boolean = canShare(track.source)

    /** The same question for an album, artist or playlist, which have no `UnifiedTrack`. */
    fun canShare(source: SourceType): Boolean =
        musicRepository.sources.any { it.sourceType == source && it.capabilities.share }

    /**
     * [speedPitch] is how the sharer was listening, and travels on the link.
     *
     * Only the player knows it, so every other screen shares at the defaults and the parameter
     * never appears in their links. Sharing a track you have pitched down is sharing the version
     * you meant rather than the one the file happens to hold.
     */
    suspend fun share(track: UnifiedTrack, speedPitch: SpeedAndPitch = SpeedAndPitch()) = share(
        ShareTarget(
            kind = ShareKind.TRACK,
            source = track.source,
            id = track.id,
            title = track.title,
            subtitle = track.artist
        ),
        speedPitch
    )

    suspend fun share(target: ShareTarget, speedPitch: SpeedAndPitch = SpeedAndPitch()) {
        val source = musicRepository.sources
            .firstOrNull { it.sourceType == target.source && it.capabilities.share }
            ?: return

        source.createShareLink(target).fold(
            onSuccess = { url ->
                _links.tryEmit(ShareLink(target = target, url = shareLinkRewriter.rewrite(url, speedPitch)))
            },
            onFailure = {
                // Sharing is off by default on a fresh Navidrome, and the server says so — which
                // is a setting the user can go and change, so the reason is worth passing on.
                _errors.tryEmit(it.message ?: "Couldn't create a share link for that.")
            }
        )
    }
}

/** A minted link, ready to hand to the system share sheet. */
data class ShareLink(
    val target: ShareTarget,
    val url: String
)
