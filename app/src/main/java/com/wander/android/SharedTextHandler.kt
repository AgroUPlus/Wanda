package com.wander.android

import android.content.Intent
import android.net.Uri
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.ShortLinkExpander
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.repository.LinkRepository
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.LaunchNotices
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text shared into Wanda from another app's share sheet (`ACTION_SEND`, `text/plain`).
 *
 * The text is only a carrier: what matters is the link in it. A link this app already opens when
 * tapped — a YouTube or YouTube Music track, a `frwd.top` listen or jam link, a universal Wanda
 * link — is turned into the same `ACTION_VIEW` a tap would have sent, so it takes exactly the path
 * a tapped link does in [AppIntentHandler]. A playlist on a service the importer reads (Spotify,
 * Deezer, Apple Music) opens the importer on it. Anything else gets an honest "can't open".
 */
@Singleton
internal class SharedTextHandler @Inject constructor(
    private val linkRepository: LinkRepository,
    private val spotifyParser: SpotifyPlaylistParser,
    private val deezerParser: DeezerPlaylistParser,
    private val deepLinkRouter: DeepLinkRouter,
    private val notices: LaunchNotices
) {

    /**
     * What [AppIntentHandler] should be given for [intent]: the intent itself when it is not a
     * share, the equivalent `ACTION_VIEW` when the shared link is one that handler opens, or null
     * when the share was dealt with here.
     *
     * [firstDelivery] is false when the Activity is being recreated with the intent it already
     * had — a rotation — and the share was acted on then; it is not acted on twice.
     */
    fun resolve(intent: Intent?, firstDelivery: Boolean): Intent? {
        if (intent?.action != Intent.ACTION_SEND) return intent
        if (!firstDelivery) return null
        val link = SharedText.firstLink(intent.getStringExtra(Intent.EXTRA_TEXT))
        when {
            link == null -> notices.post(R.string.share_link_unsupported)
            isImportablePlaylist(link) -> deepLinkRouter.request(Routes.importPlaylist(link))
            opensAsLink(Uri.parse(link)) -> return Intent(Intent.ACTION_VIEW, Uri.parse(link))
            else -> notices.post(R.string.share_link_unsupported)
        }
        return null
    }

    /**
     * A playlist the importer can read. YouTube is left out on purpose: [LinkRepository] already
     * opens a YouTube playlist link by playing it, which is what tapping one does.
     */
    private fun isImportablePlaylist(link: String): Boolean = when (PlatformType.detect(link)) {
        PlatformType.SPOTIFY -> spotifyParser.extractPlaylistId(link) != null ||
            ShortLinkExpander.isShortLink(link)
        PlatformType.DEEZER -> deezerParser.extractPlaylistId(link) != null ||
            ShortLinkExpander.isShortLink(link)
        PlatformType.APPLE_MUSIC -> link.contains("/playlist/")
        PlatformType.YOUTUBE, PlatformType.PLAIN_TEXT -> false
    }

    /** The same links [AppIntentHandler] acts on rather than turning away. Cheap, no network. */
    private fun opensAsLink(uri: Uri): Boolean =
        uri.scheme == "wanda" ||
            uri.host?.lowercase() == FRWD_HOST ||
            linkRepository.isPlaylistLink(uri) ||
            linkRepository.isAlbumLink(uri) ||
            linkRepository.isTrackLink(uri) ||
            linkRepository.canOpen(uri) ||
            linkRepository.sharedEntity(uri) != null

    private companion object {
        /** The listen and jam links' own domain, claimed by the manifest's VIEW filter too. */
        const val FRWD_HOST = "frwd.top"
    }
}
