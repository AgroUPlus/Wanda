package com.wander.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawImportTrack
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.repository.ArtistFetch
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.repository.LinkRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistImportRepository
import com.wander.android.data.repository.ShareLinkRewriter
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.repository.AgroPlaylistLink
import com.wander.android.data.repository.UniversalPlaylistLink
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.AgroPlaylistApi
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
    private val playlistImporter: PlaylistImportRepository,
    private val agroPlaylists: AgroPlaylistApi,
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
            linkRepository.isPlaylistLink(uri) -> openSharedPlaylist(scope, uri)
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

    /**
     * A playlist someone shared: saved at once as placeholders, then matched to this device's own
     * sources in the background — the same path as an import from another service.
     */
    private fun openSharedPlaylist(scope: CoroutineScope, uri: Uri) {
        if (AgroPlaylistLink.isAgroLink(uri.toString())) {
            openAgroPlaylist(scope, AgroPlaylistLink.parse(uri.toString()))
            return
        }
        val link = UniversalPlaylistLink.parse(uri.toString())
        if (link == null) {
            Toast.makeText(context, R.string.link_playlist_invalid, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val playlist = RawImportPlaylist(
                platform = PlatformType.PLAIN_TEXT,
                title = link.name,
                description = "Shared playlist",
                tracks = link.tracks.map { RawImportTrack(it.title, it.artist, it.album, it.durationMs) }
            )
            playlistImporter.savePending(playlist).fold(
                onSuccess = { id ->
                    Toast.makeText(context, context.getString(R.string.link_playlist_saved, link.name), Toast.LENGTH_LONG).show()
                    deepLinkRouter.request(Routes.playlist(id))
                },
                onFailure = { cause ->
                    Toast.makeText(context, cause.message ?: context.getString(R.string.link_playlist_invalid), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    /** A playlist on the Agro server this device is paired with, fetched by its id and saved like any other. */
    private fun openAgroPlaylist(scope: CoroutineScope, id: String?) {
        if (id == null) {
            Toast.makeText(context, R.string.link_playlist_invalid, Toast.LENGTH_LONG).show()
            return
        }
        if (!agroPlaylists.isAvailable) {
            Toast.makeText(context, R.string.link_playlist_needs_agro, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            agroPlaylists.fetch(id).fold(
                onSuccess = { shared ->
                    val playlist = RawImportPlaylist(
                        platform = PlatformType.PLAIN_TEXT,
                        title = shared.title.ifBlank { "Shared playlist" },
                        description = "Shared playlist",
                        tracks = shared.tracks.map { RawImportTrack(it.title, it.artist, it.album, it.durationMs) }
                    )
                    playlistImporter.savePending(playlist).fold(
                        onSuccess = { saved ->
                            Toast.makeText(context, context.getString(R.string.link_playlist_saved, playlist.title), Toast.LENGTH_LONG).show()
                            deepLinkRouter.request(Routes.playlist(saved))
                        },
                        onFailure = { Toast.makeText(context, it.message ?: context.getString(R.string.link_playlist_invalid), Toast.LENGTH_LONG).show() }
                    )
                },
                onFailure = { Toast.makeText(context, context.getString(R.string.link_playlist_agro_failed, it.message), Toast.LENGTH_LONG).show() }
            )
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
                YouTubeEntityKind.ARTIST -> (catalogRepository.artistDetails(id) as? ArtistFetch.Found)
                    ?.page
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
