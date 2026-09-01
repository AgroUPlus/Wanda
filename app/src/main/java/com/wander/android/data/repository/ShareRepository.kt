package com.wander.android.data.repository

import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
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

    /**
     * Shares an album as a link that does not name a backend.
     *
     * Albums do not go through [share] with the source's own link, and that is the decision here.
     * Sending someone a record is the commonest reason to share one, and a Navidrome URL is a
     * private address they cannot reach while a YouTube Music URL opens YouTube Music. A link
     * describing the album itself works for the recipient whatever they have configured — see
     * [UniversalAlbumLink].
     *
     * No network call, so unlike every other share this cannot fail. It also works for a source
     * with no sharing capability at all, including the local library.
     */
    fun shareAlbum(album: UnifiedAlbum) {
        val link = UniversalAlbumLink(
            title = album.title,
            artist = album.artist,
            year = album.year,
            trackCount = album.songCount.takeIf { it > 0 }
        )
        _links.tryEmit(
            ShareLink(
                target = ShareTarget(
                    kind = ShareKind.ALBUM,
                    source = album.source,
                    id = album.id,
                    title = album.title,
                    subtitle = album.artist
                ),
                url = link.toUri()
            )
        )
    }

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
