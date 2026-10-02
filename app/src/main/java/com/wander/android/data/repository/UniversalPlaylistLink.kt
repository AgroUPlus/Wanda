package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A playlist described by what is in it, rather than by where the sender had it.
 *
 * The same idea as [UniversalAlbumLink], for the same reason: a backend's own link is an address
 * the recipient may not be able to reach. This one carries each track's title and artist and
 * nothing that locates it, so the receiving device saves the playlist and matches the tracks
 * against whatever *it* has configured — see `PlaylistImportWorker`.
 *
 * ```
 * wanda://playlist?name=Road%20trip&d=H4sIAAAA…
 * ```
 *
 * `d` is the track list as JSON, gzipped, base64url without padding. The list is bounded on both
 * sides: a link holds at most [MAX_TRACKS] tracks, and a received one is refused if it unpacks to
 * more than [MAX_JSON_BYTES] — it arrives from anyone, and a few kilobytes of gzip can otherwise
 * unpack to gigabytes. Nothing here identifies the sender.
 */
internal data class UniversalPlaylistLink(
    val name: String,
    val tracks: List<Entry>
) {

    @Serializable
    data class Entry(
        val title: String,
        val artist: String = "",
        val album: String? = null,
        val durationMs: Long = 0L
    )

    fun toUri(): String {
        val json = Json.encodeToString(ENTRIES, tracks)
        val zipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
        return "$PREFIX?" + UniversalLinkCodec.buildQuery(listOf("name" to name, "d" to data))
    }

    internal companion object {
        const val SCHEME = "wanda"
        const val HOST = "playlist"

        /** Most tracks one link holds. Above this the link stops being something a chat will carry. */
        const val MAX_TRACKS = 200
        private const val MAX_JSON_BYTES = 512 * 1024
        private const val MAX_NAME = 200
        private const val MAX_FIELD = 300

        private const val PREFIX = "$SCHEME://$HOST"
        private val ENTRIES = ListSerializer(Entry.serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun matches(uri: String): Boolean = uri.trim().startsWith("$PREFIX?")

        /** A link for [tracks], or null when there are none or more than [MAX_TRACKS] to fit. */
        fun from(name: String, tracks: List<UnifiedTrack>): UniversalPlaylistLink? {
            if (name.isBlank() || tracks.isEmpty() || tracks.size > MAX_TRACKS) return null
            return UniversalPlaylistLink(
                name = name.trim().take(MAX_NAME),
                tracks = tracks.map {
                    Entry(it.title, it.artist, it.album?.takeIf { album -> album.isNotBlank() }, it.durationMs)
                }
            )
        }

        /** Reads a link, or null when it is not one, is damaged, or is larger than any link made here. */
        fun parse(uri: String): UniversalPlaylistLink? {
            if (!matches(uri)) return null
            val parameters = UniversalLinkCodec.parseQuery(uri)
            val name = parameters["name"]?.trim().orEmpty().take(MAX_NAME)
            val data = parameters["d"]
            if (name.isEmpty() || data.isNullOrEmpty()) return null

            return try {
                val unzipped = unzip(Base64.getUrlDecoder().decode(data)) ?: return null
                val entries = json.decodeFromString(ENTRIES, unzipped)
                    .asSequence()
                    .filter { it.title.isNotBlank() }
                    .take(MAX_TRACKS)
                    .map { it.copy(title = it.title.take(MAX_FIELD), artist = it.artist.take(MAX_FIELD), album = it.album?.take(MAX_FIELD)) }
                    .toList()
                if (entries.isEmpty()) null else UniversalPlaylistLink(name, entries)
            } catch (_: IllegalArgumentException) {
                // Bad base64 or JSON that is not a track list (a SerializationException is one too).
                null
            } catch (_: IOException) {
                // Not gzip, or cut short.
                null
            }
        }

        /** The text inside [zipped], or null if it is longer than [MAX_JSON_BYTES] once unpacked. */
        private fun unzip(zipped: ByteArray): String? {
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            GZIPInputStream(ByteArrayInputStream(zipped)).use { input ->
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    if (out.size() + read > MAX_JSON_BYTES) return null
                    out.write(chunk, 0, read)
                }
            }
            return out.toString(Charsets.UTF_8)
        }
    }
}
