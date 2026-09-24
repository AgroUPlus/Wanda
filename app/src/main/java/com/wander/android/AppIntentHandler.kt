package com.wander.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.repository.LinkRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareLinkRewriter
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.explain
import com.wander.android.data.sources.ytmusic.YouTubeEntity
import com.wander.android.data.sources.ytmusic.YouTubeEntityKind
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.navigation.TopLevelDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles incoming intent URI routing, including deep links, Agro pairing, friend codes,
 * Jam invites, and shared media links.
 */
@Singleton
internal class AppIntentHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerConnection: PlayerConnection,
    private val secureStorage: SecureStorage,
    private val agroClient: AgroClient,
    private val linkRepository: LinkRepository,
    private val musicRepository: MusicRepository,
    private val catalogRepository: CatalogRepository,
    private val shareLinkRewriter: ShareLinkRewriter,
    private val deepLinkRouter: DeepLinkRouter,
    private val socialRepository: SocialRepository
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
            uri.scheme == "agro" -> handleAgroPairing(scope, uri)
            uri.scheme == "wanda" && uri.host == "inbox" -> deepLinkRouter.request(Routes.ACTIVITY)
            uri.scheme == "wanda" && uri.host == "fingerprints" -> deepLinkRouter.request(Routes.FINGERPRINTS)
            uri.scheme == "wanda" && uri.host == "friend" -> handleFriendCode(scope, uri)
            isJamLink(uri) -> handleJamLink(uri)
            linkRepository.isAlbumLink(uri) -> openSharedAlbum(scope, uri)
            linkRepository.isTrackLink(uri) -> openSharedTrack(scope, uri)
            linkRepository.canOpen(uri) -> openSharedLink(scope, uri)
            entity != null -> openSharedEntity(scope, entity)
            uri.scheme == "https" || uri.scheme == "wanda" -> Toast.makeText(
                context,
                "That link isn't something Wanda can open.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleFriendCode(scope: CoroutineScope, uri: Uri) {
        val code = uri.lastPathSegment?.trim().orEmpty()
        if (code.isEmpty()) return
        scope.launch {
            val friend = socialRepository.redeemFriendCode(code).getOrNull()
            val message = friend
                ?.let { "You and @$it are now friends" }
                ?: "That code has expired or has already been used."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (friend != null) deepLinkRouter.request(TopLevelDestination.FRIENDS.route)
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

    private fun openSharedAlbum(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolveAlbum(uri).fold(
                onSuccess = { album ->
                    val tracks = musicRepository.getAlbumTracks(album)
                    if (tracks.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Found “${album.title}” but couldn't load its tracks.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        playerConnection.play(tracks)
                    }
                },
                onFailure = { cause ->
                    Toast.makeText(
                        context,
                        cause.message ?: "Couldn't open that album link.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun openSharedTrack(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolveTrack(uri).fold(
                onSuccess = { track -> playerConnection.play(listOf(track)) },
                onFailure = { cause ->
                    Toast.makeText(
                        context,
                        cause.message ?: context.getString(R.string.link_track_open_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun openSharedEntity(scope: CoroutineScope, entity: YouTubeEntity) {
        scope.launch {
            val id = SourceType.YTMUSIC.idPrefix + entity.id
            val tracks = when (entity.kind) {
                YouTubeEntityKind.ALBUM -> musicRepository.getAlbumTracksById(id)
                YouTubeEntityKind.PLAYLIST -> musicRepository.getPlaylistTracksById(id)
                YouTubeEntityKind.ARTIST -> catalogRepository.artistDetails(id)
                    ?.sections
                    ?.filterIsInstance<ArtistTrackSection>()
                    ?.firstOrNull()
                    ?.tracks
                    .orEmpty()
            }
            if (tracks.isEmpty()) {
                Toast.makeText(
                    context,
                    "Nothing playable behind that link.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                playerConnection.play(tracks)
            }
        }
    }

    private fun openSharedLink(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolve(uri).fold(
                onSuccess = { track ->
                    musicRepository.rememberSharedTrack(track)
                    playerConnection.play(listOf(track))
                    shareLinkRewriter.playbackOf(uri)?.let {
                        playerConnection.setSpeedAndPitch(it.speed, it.pitch)
                    }
                },
                onFailure = { cause ->
                    Toast.makeText(
                        context,
                        cause.message ?: "Couldn't open that link.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun handleAgroPairing(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            val result = agroClient.parseQrCodePayload(uri.toString())
            val message = result.fold(
                onSuccess = { petname ->
                    "Paired with Agro as ${petname ?: secureStorage.agroDevicePetname.ifEmpty { "wanda" }}"
                },
                onFailure = { error ->
                    android.util.Log.w("Wanda", "Agro pairing failed: ${error.message}")
                    AgroAuthError.from(error).explain()
                }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
