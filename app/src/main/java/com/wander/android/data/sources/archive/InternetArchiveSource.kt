package com.wander.android.data.sources.archive

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFIX = "archive:"

/** Public-domain and freely-licensed audio. No account, so no likes and no scrobbling. */
@Singleton
class InternetArchiveSource @Inject constructor(
    private val apiClient: ArchiveApiClient
) : IMusicSource {

    override val sourceType = SourceType.INTERNET_ARCHIVE
    override val displayName = "Internet Archive"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        playlists = true,
        radio = true
    )

    /** Anonymous and always available. */
    override val isConfigured: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    /**
     * Archive's search returns *items* (a concert, a record), not songs. A track list therefore
     * needs a second call per item; a handful of the top hits are expanded concurrently so the
     * user gets playable tracks rather than unplayable item stubs.
     */
    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        apiClient.searchAudio(query, SEARCH_ITEMS).mapCatching { docs ->
            coroutineScope {
                docs.take(EXPAND_ITEMS)
                    .map { doc -> async { tracksOf(doc.identifier).getOrDefault(emptyList()) } }
                    .flatMap { it.await() }
            }
        }

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        val (identifier, fileName) = trackId.removePrefix(PREFIX).splitItemAndFile()
        if (fileName == null) {
            return Result.failure(IOException("Archive track id is missing a file: $trackId"))
        }
        return apiClient.getMetadata(identifier).mapCatching { meta ->
            val file = meta.files.orEmpty().firstOrNull { it.name == fileName }
                ?: throw IOException("$fileName is no longer part of $identifier")
            StreamInfo(
                uri = apiClient.buildDirectAudioUrl(
                    server = meta.server ?: throw IOException("Archive did not return a server"),
                    dir = meta.dir.orEmpty(),
                    fileName = file.name
                ),
                format = file.format ?: "audio/mpeg",
                bitRateKbps = file.bitrate?.toIntOrNull() ?: 192,
                isDirectFile = true
            )
        }
    }

    override suspend fun getRadio(seedTrackId: String, count: Int) =
        collectionTracks(COLLECTION_LIVE, count)

    override suspend fun getRecentTracks(limit: Int) = collectionTracks(COLLECTION_LIVE, limit)

    override suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        apiClient.getCollectionAudio(COLLECTION_LIVE, limit).map { docs ->
            docs.map { doc ->
                UnifiedAlbum(
                    id = "$PREFIX${doc.identifier}",
                    source = SourceType.INTERNET_ARCHIVE,
                    title = doc.title ?: doc.identifier,
                    artist = doc.creator ?: "Internet Archive",
                    coverArtUrl = apiClient.buildCoverArtUrl(doc.identifier),
                    year = doc.year?.toIntOrNull()
                )
            }
        }

    override suspend fun getAlbumTracks(albumId: String) =
        tracksOf(albumId.removePrefix(PREFIX).splitItemAndFile().first)

    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> = Result.success(
        COLLECTIONS.map { (id, description) ->
            UnifiedPlaylist(
                id = "${PREFIX}collection:$id",
                source = SourceType.INTERNET_ARCHIVE,
                name = description.first,
                comment = description.second
            )
        }
    )

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> {
        val collection = playlistId.substringAfter("collection:", COLLECTION_LIVE)
        return collectionTracks(collection, COLLECTION_ITEMS)
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────

    /** Expands one item into its songs, choosing the best available format for each. */
    private suspend fun tracksOf(identifier: String): Result<List<UnifiedTrack>> =
        apiClient.getMetadata(identifier).map { meta ->
            val albumTitle = meta.metadata?.title ?: identifier
            val creator = meta.metadata?.creator ?: "Internet Archive"
            val cover = apiClient.buildCoverArtUrl(identifier)

            meta.files.orEmpty().bestAudioPerRecording().map { file ->
                UnifiedTrack(
                    id = "$PREFIX$identifier#${file.name}",
                    source = SourceType.INTERNET_ARCHIVE,
                    title = file.title ?: file.name.substringBeforeLast('.'),
                    artist = file.creator ?: creator,
                    album = file.album ?: albumTitle,
                    albumId = "$PREFIX$identifier",
                    artworkUrl = cover,
                    durationMs = file.length.toDurationMs(),
                    trackNumber = file.track?.toIntOrNull(),
                    year = meta.metadata?.year?.toIntOrNull(),
                    format = file.format
                )
            }
        }

    private suspend fun collectionTracks(collection: String, limit: Int): Result<List<UnifiedTrack>> =
        apiClient.getCollectionAudio(collection, COLLECTION_ITEMS).mapCatching { docs ->
            coroutineScope {
                docs.take(EXPAND_ITEMS)
                    .map { doc -> async { tracksOf(doc.identifier).getOrDefault(emptyList()) } }
                    .flatMap { it.await() }
                    .take(limit)
            }
        }

    private companion object {
        const val COLLECTION_LIVE = "etree"
        const val SEARCH_ITEMS = 20
        const val COLLECTION_ITEMS = 12
        /** Each expansion is an extra request, so widen breadth only as far as it stays snappy. */
        const val EXPAND_ITEMS = 6

        val COLLECTIONS = listOf(
            "audio" to ("All Audio" to "Everything in the Archive's audio collection"),
            "etree" to ("Live Music Archive" to "Lossless, artist-sanctioned concert recordings"),
            "netlabels" to ("Netlabels" to "Freely licensed releases from independent labels"),
            "78rpm" to ("Great 78 Project" to "Digitised 78rpm records and cylinders")
        )
    }
}

/** `archive:<item>#<file>` — the file part is absent for item-level ids such as albums. */
private fun String.splitItemAndFile(): Pair<String, String?> {
    val index = indexOf('#')
    return if (index < 0) this to null else substring(0, index) to substring(index + 1)
}

/** Archive reports length either as seconds (`182.5`) or as `mm:ss`. */
private fun String?.toDurationMs(): Long {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return 0L
    raw.toDoubleOrNull()?.let { return (it * 1000).toLong() }
    val parts = raw.split(':').mapNotNull { it.toDoubleOrNull() }
    if (parts.isEmpty()) return 0L
    val seconds = parts.fold(0.0) { acc, part -> acc * 60 + part }
    return (seconds * 1000).toLong()
}
