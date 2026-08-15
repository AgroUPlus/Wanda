package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

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
    fun canShare(track: UnifiedTrack): Boolean =
        musicRepository.sources.any { it.sourceType == track.source && it.capabilities.share }

    suspend fun share(track: UnifiedTrack) {
        val source = musicRepository.sources
            .firstOrNull { it.sourceType == track.source && it.capabilities.share }
            ?: return

        source.createShareLink(track.id, "${track.title} — ${track.artist}").fold(
            onSuccess = { url ->
                _links.tryEmit(ShareLink(track = track, url = shareLinkRewriter.rewrite(url)))
            },
            onFailure = {
                // Sharing is off by default on a fresh Navidrome, and the server says so — which
                // is a setting the user can go and change, so the reason is worth passing on.
                _errors.tryEmit(it.message ?: "Couldn't create a share link for that track.")
            }
        )
    }
}

/** A minted link, ready to hand to the system share sheet. */
data class ShareLink(
    val track: UnifiedTrack,
    val url: String
)
