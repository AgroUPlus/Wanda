package com.wander.android.core.playback.browse

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wander.android.R
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.data.repository.SmartMixRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tree a car, or any other media browser this app lets in, walks:
 *
 * - **Home** — Recently played, Most played, and the smart mixes Home shows.
 * - **Library** — Liked, Albums, Downloads.
 * - **Playlists** — Wanda's own playlists.
 * - **Podcasts** — episodes started and not finished.
 *
 * Four at the root because a car draws the root's children as tabs, and Android Auto shows no
 * more than four of them. Everything is read from Room; see [BrowseTracks].
 *
 * Children are returned a page at a time. A browser that asks for no paging at all — the legacy
 * protocol Android Auto speaks does, as one page of [Int.MAX_VALUE] — still gets at most
 * [MAX_PAGE_SIZE], because every item crosses a binder with its artwork bitmap attached.
 */
@Singleton
internal class BrowseTree @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tracks: BrowseTracks,
    private val libraryBrowse: LibraryBrowseRepository,
    private val playlistDao: PlaylistDao,
    private val albumDao: AlbumDao,
    private val smartMixes: SmartMixRepository
) {

    val root: MediaItem
        get() = folder(BrowseId.Root, R.string.app_name, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    /** One page of [node]'s children, or null when [node] does not exist (any more). */
    suspend fun children(node: BrowseId.Node, page: Int, pageSize: Int): List<MediaItem>? {
        val from = page.toLong() * pageSize
        if (page < 0 || pageSize < 1 || from > Int.MAX_VALUE) return null
        val window = Window(from.toInt(), pageSize.coerceAtMost(MAX_PAGE_SIZE))
        return when (node) {
            BrowseId.Root -> window.of(
                listOf(BrowseId.Home, BrowseId.Library, BrowseId.Playlists, BrowseId.Podcasts).map { fixed(it) }
            )
            BrowseId.Home -> window.of(
                listOf(fixed(BrowseId.Recent), fixed(BrowseId.MostPlayed)) + mixFolders()
            )
            BrowseId.Library -> window.of(listOf(BrowseId.Liked, BrowseId.Albums, BrowseId.Downloads).map { fixed(it) })
            BrowseId.Playlists -> window.of(playlistDao.getAllPlaylists()).map {
                folder(BrowseId.Playlist(it.id), it.name, PLAYLIST, artworkUrl = it.coverArtUrl)
            }
            BrowseId.Albums -> window.of(libraryBrowse.albums(LibrarySort.RECENTLY_PLAYED, emptySet()).first())
                .map(::albumFolder)
            else -> tracks.of(node)?.let { window.of(it) }?.map {
                trackItem(node, it, partlyPlayed = node == BrowseId.Podcasts)
            }
        }
    }

    /** The item [id] names, as it was listed, or null when it does not exist (any more). */
    suspend fun item(id: BrowseId): MediaItem? = when (id) {
        is BrowseId.Track -> tracks.track(id.trackId)?.let {
            trackItem(id.parent, it, partlyPlayed = id.parent == BrowseId.Podcasts)
        }
        BrowseId.Root -> root
        is BrowseId.Mix -> mixFolders().firstOrNull { it.mediaId == id.encode() }
        is BrowseId.Playlist -> playlistDao.getPlaylistById(id.playlistId)?.let {
            folder(id, it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, artworkUrl = it.coverArtUrl)
        }
        is BrowseId.Album -> albumDao.getAlbumById(id.albumId)?.toUnifiedAlbum()?.let(::albumFolder)
        is BrowseId.Search -> folder(
            id, context.getString(R.string.browse_search_results, id.query), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
        is BrowseId.Node -> fixed(id)
    }

    /** One page of library tracks matching [query], or null for a page that cannot exist. */
    suspend fun searchResults(query: String, page: Int, pageSize: Int): List<MediaItem>? =
        children(BrowseId.Search(query), page, pageSize)

    /** How many library tracks match [query], capped as [BrowseTracks.search] caps them. */
    suspend fun searchCount(query: String): Int = tracks.search(query).size

    private suspend fun mixFolders(): List<MediaItem> = smartMixes.getSmartMixes().map { mix ->
        folder(
            BrowseId.Mix(mix.seedType),
            context.getString(mix.title),
            MediaMetadata.MEDIA_TYPE_PLAYLIST,
            subtitle = context.getString(mix.subtitle),
            // A mix is a list worth playing as a whole, not only a list to pick from.
            playable = true
        )
    }

    private fun albumFolder(album: UnifiedAlbum): MediaItem = folder(
        BrowseId.Album(album.id),
        album.title,
        MediaMetadata.MEDIA_TYPE_ALBUM,
        subtitle = album.artist,
        artworkUrl = album.coverArtUrl,
        playable = true
    )

    /** The fixed folders, which have nothing to look up. */
    private fun fixed(node: BrowseId.Node): MediaItem? = when (node) {
        BrowseId.Root -> root
        BrowseId.Home -> folder(node, R.string.browse_home, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseId.Library -> folder(node, R.string.browse_library, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        BrowseId.Playlists -> folder(node, R.string.browse_playlists, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
        BrowseId.Podcasts -> folder(node, R.string.browse_podcasts, MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
        BrowseId.Recent -> folder(node, R.string.browse_recent, PLAYLIST, playable = true)
        BrowseId.MostPlayed -> folder(node, R.string.browse_most_played, PLAYLIST, playable = true)
        BrowseId.Liked -> folder(node, R.string.browse_liked, PLAYLIST, playable = true)
        BrowseId.Downloads -> folder(node, R.string.browse_downloads, PLAYLIST, playable = true)
        BrowseId.Albums -> folder(node, R.string.browse_albums, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, grid = true)
        is BrowseId.Mix, is BrowseId.Playlist, is BrowseId.Album, is BrowseId.Search -> null
    }

    private fun folder(
        node: BrowseId.Node,
        @StringRes title: Int,
        mediaType: Int,
        grid: Boolean = false,
        playable: Boolean = false
    ): MediaItem = folderItem(node, context.getString(title), mediaType, grid = grid, playable = playable)

    private fun folder(
        node: BrowseId.Node,
        title: CharSequence,
        mediaType: Int,
        subtitle: CharSequence? = null,
        artworkUrl: String? = null,
        playable: Boolean = false
    ): MediaItem = folderItem(node, title, mediaType, subtitle, artworkUrl, playable = playable)

    private class Window(val from: Int, val size: Int) {
        fun <T> of(all: List<T?>): List<T> = all.filterNotNull().drop(from).take(size)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val PLAYLIST = MediaMetadata.MEDIA_TYPE_PLAYLIST
    }
}
