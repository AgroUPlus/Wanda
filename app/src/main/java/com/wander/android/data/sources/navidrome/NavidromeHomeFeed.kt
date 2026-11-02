package com.wander.android.data.sources.navidrome

import android.content.Context
import androidx.media3.common.MimeTypes
import com.wander.android.R
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Navidrome's contribution to Home: what the server itself knows about this account's listening.
 * Newest and most played records (`getAlbumList2`), a random pick (`getRandomSongs`), the top
 * songs of the artists played most (`getTopSongs`), and the server's internet radio stations.
 *
 * A shelf whose call fails is left out; the feed fails only when every call did.
 */
@Singleton
class NavidromeHomeFeed @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiClient: SubsonicApiClient,
    private val catalogLoader: NavidromeCatalogLoader
) {
    suspend fun shelves(): Result<List<RecommendedShelf>> = coroutineScope {
        val frequentAlbums = async { apiClient.getAlbumList2(type = "frequent", size = ALBUMS_PER_SHELF) }
        val recent = async { albumShelf("newest", R.string.navidrome_shelf_recent, SHELF_RECENT) }
        val frequent = async {
            frequentAlbums.await().mapCatching { albums -> albumSongs(albums, mostPlayed = true) }
                .map { RecommendedShelf(SHELF_FREQUENT, context.getString(R.string.navidrome_shelf_frequent), it) }
        }
        val random = async {
            apiClient.getRandomSongs(SHELF_SIZE).map { songs ->
                RecommendedShelf(SHELF_RANDOM, context.getString(R.string.navidrome_shelf_random), songs.unified())
            }
        }
        val topArtists = async { topArtistsShelf(frequentAlbums.await()) }
        val radio = async { radioShelf() }

        val results = listOf(recent, frequent, random, topArtists, radio).awaitAll()
        val shelves = results.mapNotNull { it.getOrNull() }.filter { it.tracks.isNotEmpty() }
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        if (shelves.isEmpty() && failure != null) Result.failure(failure) else Result.success(shelves)
    }

    /** A station by its track id, for playback. Fails for one the server no longer lists. */
    suspend fun radioStream(trackId: String): Result<StreamInfo> {
        val id = trackId.removePrefix(RADIO_PREFIX)
        return apiClient.getInternetRadioStations().mapCatching { stations ->
            val station = stations.firstOrNull { it.id == id }
                ?: throw IOException("This radio station is no longer on the server")
            StreamInfo(uri = station.streamUrl, format = MimeTypes.APPLICATION_M3U8)
        }
    }

    private suspend fun albumShelf(type: String, title: Int, id: String): Result<RecommendedShelf> =
        apiClient.getAlbumList2(type = type, size = ALBUMS_PER_SHELF)
            .mapCatching { albums -> albumSongs(albums, mostPlayed = false) }
            .map { RecommendedShelf(id, context.getString(title), it) }

    /** A few songs from each album: its most played ones, or its first ones. */
    private suspend fun albumSongs(albums: List<SubsonicAlbum>, mostPlayed: Boolean): List<UnifiedTrack> =
        coroutineScope {
            albums.map { album -> async { apiClient.getAlbum(album.id) } }.awaitAll()
                .flatMap { result ->
                    val songs = result.getOrThrow().song.orEmpty()
                    (if (mostPlayed) songs.sortedByDescending { it.playCount } else songs).take(SONGS_PER_ALBUM)
                }
                .take(SHELF_SIZE)
                .unified()
        }

    private suspend fun topArtistsShelf(frequent: Result<List<SubsonicAlbum>>): Result<RecommendedShelf> =
        coroutineScope {
            val artists = frequent.getOrElse { return@coroutineScope Result.failure(it) }
                .mapNotNull { it.artist?.takeIf(String::isNotBlank) }
                .distinct()
                .take(TOP_ARTISTS)
            val lists = artists.map { artist -> async { apiClient.getTopSongs(artist, SONGS_PER_ARTIST) } }.awaitAll()
            val songs = lists.flatMap { it.getOrNull().orEmpty() }
            val failure = lists.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (songs.isEmpty() && failure != null) {
                Result.failure(failure)
            } else {
                Result.success(
                    RecommendedShelf(SHELF_TOP_ARTISTS, context.getString(R.string.navidrome_shelf_top_artists), songs.unified())
                )
            }
        }

    /**
     * Only HLS stations. Playback treats every live track as an HLS manifest, and a plain Icecast
     * stream handed over as one fails to parse; those stay off the shelf rather than failing.
     */
    private suspend fun radioShelf(): Result<RecommendedShelf> =
        apiClient.getInternetRadioStations().map { stations ->
            val tracks = stations.filter { it.isHls() }.map { station ->
                UnifiedTrack(
                    id = "$RADIO_PREFIX${station.id}",
                    source = SourceType.NAVIDROME,
                    title = station.name,
                    artist = context.getString(R.string.navidrome_radio_artist),
                    isLive = true
                )
            }
            RecommendedShelf(SHELF_RADIO, context.getString(R.string.navidrome_shelf_radio), tracks)
        }

    private fun SubsonicRadioStation.isHls(): Boolean =
        streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    private fun List<SubsonicSong>.unified(): List<UnifiedTrack> =
        with(catalogLoader) { distinctBy { it.id }.map { it.toUnified() } }

    internal companion object {
        /** Station track ids, apart from song ids so playback can tell them from a song. */
        const val RADIO_PREFIX = "${NAVIDROME_PREFIX}radio:"

        const val SHELF_RECENT = "navidrome_recent"
        const val SHELF_FREQUENT = "navidrome_frequent"
        const val SHELF_RANDOM = "navidrome_random"
        const val SHELF_TOP_ARTISTS = "navidrome_top_artists"
        const val SHELF_RADIO = "navidrome_radio"

        const val SHELF_SIZE = 20
        const val ALBUMS_PER_SHELF = 7
        const val SONGS_PER_ALBUM = 3
        const val TOP_ARTISTS = 4
        const val SONGS_PER_ARTIST = 5
    }
}
