package com.wander.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wander.android.data.repository.LinkRepository
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.screens.settings.SettingsCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes an incoming intent to what handles it: deep links into a screen and Jam invites here,
 * pairing and friend codes to [ConfirmedLinkHandler], shared music to [SharedMediaLinkOpener].
 */
@Singleton
internal class AppIntentHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linkRepository: LinkRepository,
    private val deepLinkRouter: DeepLinkRouter,
    private val confirmedLinks: ConfirmedLinkHandler,
    private val sharedMedia: SharedMediaLinkOpener
) {
    /**
     * Links arrive here: an `agro:` pairing QR, a YouTube/YouTube Music track
     * someone shared, a `wanda://listen` handoff, a `wanda://inbox` notification,
     * or a Jam invite link `https://frwd.top/jam?code=...` / `wanda://jam?code=...`.
     */
    fun handleIntent(scope: CoroutineScope, intent: Intent?) {
        val uri = intent?.data ?: return
        val entity = linkRepository.sharedEntity(uri)
        when {
            uri.scheme == "agro" -> confirmedLinks.handleAgroPairing(uri)
            uri.scheme == "wanda" && uri.host == "inbox" -> deepLinkRouter.request(Routes.ACTIVITY)
            uri.scheme == "wanda" && uri.host == "fingerprints" -> deepLinkRouter.request(Routes.FINGERPRINTS)
            // From the listening-together notifications: the room, and where incognito lives.
            uri.scheme == "wanda" && uri.host == "jam-room" -> deepLinkRouter.request(Routes.jam())
            uri.scheme == "wanda" && uri.host == "privacy" ->
                deepLinkRouter.request(Routes.settingsCategory(SettingsCategory.PRIVACY.name))
            uri.scheme == "wanda" && uri.host == "friend" -> confirmedLinks.handleFriendCode(uri)
            isJamLink(uri) -> handleJamLink(uri)
            linkRepository.isPlaylistLink(uri) -> sharedMedia.openSharedPlaylist(scope, uri)
            linkRepository.isAlbumLink(uri) -> sharedMedia.openSharedAlbum(scope, uri)
            linkRepository.isTrackLink(uri) -> sharedMedia.openSharedTrack(scope, uri)
            linkRepository.canOpen(uri) -> sharedMedia.openSharedLink(scope, uri)
            entity != null -> sharedMedia.openSharedEntity(scope, entity)
            uri.scheme == "https" || uri.scheme == "wanda" -> Toast.makeText(
                context,
                "That link isn't something Wanda can open.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isJamLink(uri: Uri): Boolean {
        val isScheme = uri.scheme in setOf("wanda", "https", "http")
        val isJamHostOrPath = uri.host == "jam" || uri.pathSegments.contains("jam")
        val hasCodeOrId = uri.getQueryParameter("code") != null || uri.getQueryParameter("id") != null
        return isScheme && isJamHostOrPath && hasCodeOrId
    }

    private fun handleJamLink(uri: Uri) {
        val code = uri.getQueryParameter("code")?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }?.take(10)
        if (!code.isNullOrEmpty()) {
            Toast.makeText(context, "Opening Jam $code...", Toast.LENGTH_SHORT).show()
            deepLinkRouter.request(Routes.jam(code))
        } else {
            deepLinkRouter.request(Routes.jam())
        }
    }
}
