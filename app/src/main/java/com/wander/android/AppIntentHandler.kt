package com.wander.android

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import com.wander.android.R
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.browse.VoiceQuery
import com.wander.android.core.playback.browse.VoiceSearch
import com.wander.android.data.repository.LinkRepository
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.screens.settings.SettingsCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes an incoming intent to what handles it: deep links into a screen, Jam invites and voice
 * requests here, pairing and friend codes to [ConfirmedLinkHandler], shared music to
 * [SharedMediaLinkOpener].
 */
@Singleton
internal class AppIntentHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linkRepository: LinkRepository,
    private val deepLinkRouter: DeepLinkRouter,
    private val confirmedLinks: ConfirmedLinkHandler,
    private val sharedMedia: SharedMediaLinkOpener,
    private val voiceSearch: VoiceSearch,
    private val playerConnection: PlayerConnection,
    private val openedFiles: OpenedAudioFile
) {
    /**
     * Links arrive here: an audio file opened from another app, an `agro:` pairing QR, a
     * YouTube/YouTube Music track someone shared, a `wanda://listen` handoff, a `wanda://inbox`
     * notification, or a Jam invite link `https://frwd.top/jam?code=...` / `wanda://jam?code=...`.
     *
     * [firstDelivery] is false when the activity was recreated with the intent it already had:
     * a voice request or an opened file is not played a second time because the screen rotated.
     */
    fun handleIntent(scope: CoroutineScope, intent: Intent?, firstDelivery: Boolean) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            if (firstDelivery) playFromSearch(scope, intent)
            return
        }
        val uri = intent?.data ?: return
        val entity = linkRepository.sharedEntity(uri)
        when {
            // "Open with": the manifest only routes audio files here on these two schemes.
            uri.scheme == "content" || uri.scheme == "file" -> if (firstDelivery) openedFiles.play(scope, uri)
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
                R.string.link_unsupported,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * "Play … on Wanda" said to the Assistant on the phone, which reaches the app as this intent
     * rather than through the media session. A car's voice requests go through the session
     * instead; both end in [VoiceSearch].
     */
    private fun playFromSearch(scope: CoroutineScope, intent: Intent) {
        val query = VoiceQuery.of(intent.getStringExtra(SearchManager.QUERY), intent.extras)
        scope.launch {
            val tracks = voiceSearch.tracksFor(query)
            if (tracks.isEmpty()) {
                Toast.makeText(context, R.string.voice_search_nothing_found, Toast.LENGTH_LONG).show()
            } else {
                playerConnection.play(tracks)
            }
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
            Toast.makeText(context, context.getString(R.string.jam_opening_code, code), Toast.LENGTH_SHORT).show()
            deepLinkRouter.request(Routes.jam(code))
        } else {
            deepLinkRouter.request(Routes.jam())
        }
    }
}
