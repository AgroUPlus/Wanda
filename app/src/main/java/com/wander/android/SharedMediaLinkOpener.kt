package com.wander.android

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.wander.android.core.network.userMessageRes
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawImportTrack
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.SourceType
import com.wander.android.data.repository.AgroPlaylistLink
import com.wander.android.data.repository.ArtistFetch
import com.wander.android.data.repository.CatalogRepository
import com.wander.android.data.repository.LinkRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistImportRepository
import com.wander.android.data.repository.ShareLinkRewriter
import com.wander.android.data.repository.UniversalPlaylistLink
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.ytmusic.YouTubeEntity
import com.wander.android.data.sources.ytmusic.YouTubeEntityKind
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens a link to music someone shared — a playlist, an album, a track, or a YouTube page — by
 * playing it or saving it. [AppIntentHandler] decides which of these a link is.
 */
@Singleton
internal class SharedMediaLinkOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerConnection: PlayerConnection,
    private val linkRepository: LinkRepository,
    private val musicRepository: MusicRepository,
    private val playlistImporter: PlaylistImportRepository,
    private val agroPlaylists: AgroSharedPlaylistApi,
    private val sharedPlaylists: SharedPlaylistRepository,
    private val catalogRepository: CatalogRepository,
    private val shareLinkRewriter: ShareLinkRewriter,
    private val deepLinkRouter: DeepLinkRouter
) {

    /**
     * A playlist someone shared: saved at once as placeholders, then matched to this device's own
     * sources in the background — the same path as an import from another service.
     */
    fun openSharedPlaylist(scope: CoroutineScope, uri: Uri) {
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
                description = context.getString(R.string.link_shared_playlist_description),
                tracks = link.tracks.map { RawImportTrack(it.title, it.artist, it.album, it.durationMs) }
            )
            playlistImporter.savePending(playlist).fold(
                onSuccess = { id ->
                    val saved = context.getString(R.string.link_playlist_saved, link.name)
                    Toast.makeText(context, saved, Toast.LENGTH_LONG).show()
                    deepLinkRouter.request(Routes.playlist(id))
                },
                onFailure = { cause ->
                    Toast.makeText(context, cause.userMessageRes(), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    /**
     * A playlist on the Agro server this device is paired with: followed, so this device keeps a
     * live copy that follows the owner's changes, rather than saved as a snapshot that never does.
     * It can still be made into a Wanda playlist of its own from its screen.
     */
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
            sharedPlaylists.follow(id).fold(
                onSuccess = { route -> deepLinkRouter.request(Routes.playlist(route)) },
                onFailure = {
                    val message = context.getString(R.string.link_playlist_agro_failed, context.getString(it.userMessageRes()))
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun openSharedAlbum(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolveAlbum(uri).fold(
                onSuccess = { album ->
                    val tracks = musicRepository.getAlbumTracks(album).getOrNull().orEmpty()
                    if (tracks.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.link_album_tracks_failed, album.title),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        playerConnection.play(tracks)
                    }
                },
                onFailure = { _ ->
                    Toast.makeText(
                        context,
                        R.string.link_album_open_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun openSharedTrack(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolveTrack(uri).fold(
                onSuccess = { track -> playerConnection.play(listOf(track)) },
                onFailure = { _ ->
                    Toast.makeText(
                        context,
                        R.string.link_track_open_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun openSharedEntity(scope: CoroutineScope, entity: YouTubeEntity) {
        scope.launch {
            val id = SourceType.YTMUSIC.idPrefix + entity.id
            val tracks = when (entity.kind) {
                YouTubeEntityKind.ALBUM -> musicRepository.getAlbumTracksById(id).getOrNull().orEmpty()
                YouTubeEntityKind.PLAYLIST -> musicRepository.getPlaylistTracksById(id).getOrNull().orEmpty()
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
                    R.string.link_nothing_playable,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                playerConnection.play(tracks)
            }
        }
    }

    fun openSharedLink(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            linkRepository.resolve(uri).fold(
                onSuccess = { track ->
                    musicRepository.rememberSharedTrack(track)
                    playerConnection.play(listOf(track))
                    shareLinkRewriter.playbackOf(uri)?.let {
                        playerConnection.setSpeedAndPitch(it.speed, it.pitch)
                    }
                },
                onFailure = { _ ->
                    Toast.makeText(
                        context,
                        R.string.link_open_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}
