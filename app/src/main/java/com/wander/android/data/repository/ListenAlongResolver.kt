package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.agro.AgroRelayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Where a listen-along track was found, so the UI can be honest about what is playing. */
internal enum class ResolvedFrom {
    /** A local file on this device or in downloaded offline cache. */
    LOCAL_STORAGE,

    /** Streamed from your personal Navidrome server. */
    NAVIDROME,

    /** Matched and streamed from YouTube Music. */
    YOUTUBE_MUSIC,

    /** Streamed directly over LAN from the host/peer device via P2P HTTP chunks. */
    P2P_DIRECT,

    /** Streamed via Agro ephemeral server relay pipe. */
    AGRO_RELAY
}

internal data class ResolvedTrack(val track: UnifiedTrack, val from: ResolvedFrom)

/**
 * Turns a friend's or Jam's now-playing into something this device can actually play.
 *
 * Strict fallback priority hierarchy:
 * 1. Local Storage / Downloaded cache (SourceType.LOCAL)
 * 2. Navidrome server (SourceType.NAVIDROME)
 * 3. YouTube Music (SourceType.YTMUSIC)
 * 4. Direct LAN P2P audio chunks (port 8702)
 * 5. Agro Ephemeral Relay audio chunks
 * 6. Unresolved (null)
 */
@Singleton
internal class ListenAlongResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val agroRelayClient: AgroRelayClient,
    private val secureStorage: SecureStorage
) {
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolve(
        title: String,
        artist: String,
        hostUsername: String? = null,
        hostDevice: String? = null,
        hostLanAddress: String? = null,
        contentHash: String? = null
    ): ResolvedTrack? {
        if (title.isBlank()) return null
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")

        // 1. Local storage & downloaded files first (free, offline, identical file)
        val localMatches = musicRepository
            .searchAllSources(query, onlySources = setOf(SourceType.LOCAL), kind = SearchKind.TRACKS)
        val bestLocal = localMatches.bestMatch(title, artist)
            ?: if (artist.isNotBlank()) {
                musicRepository
                    .searchAllSources(title, onlySources = setOf(SourceType.LOCAL), kind = SearchKind.TRACKS)
                    .bestMatch(title, artist)
            } else null

        if (bestLocal != null) {
            return ResolvedTrack(bestLocal, ResolvedFrom.LOCAL_STORAGE)
        }

        // 2. Personal Navidrome server
        val navidromeMatches = musicRepository
            .searchAllSources(query, onlySources = setOf(SourceType.NAVIDROME), kind = SearchKind.TRACKS)
        val bestNav = navidromeMatches.bestMatch(title, artist)
        if (bestNav != null) {
            return ResolvedTrack(bestNav, ResolvedFrom.NAVIDROME)
        }

        // 3. YouTube Music streaming
        val ytmMatches = musicRepository
            .searchAllSources(query, onlySources = setOf(SourceType.YTMUSIC), kind = SearchKind.TRACKS)
        val bestYtm = ytmMatches.bestMatch(title, artist)
        if (bestYtm != null) {
            return ResolvedTrack(bestYtm, ResolvedFrom.YOUTUBE_MUSIC)
        }

        // 4. Direct LAN P2P audio streaming
        if (!hostLanAddress.isNullOrBlank()) {
            val lanUrl = "http://${hostLanAddress.trim()}:8702/p2p/stream?hash=${contentHash.orEmpty()}"
            val pingUrl = "http://${hostLanAddress.trim()}:8702/p2p/ping"
            val isLanAlive = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(pingUrl).build()
                    probeClient.newCall(req).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
            if (isLanAlive) {
                Log.i(TAG, "Resolved track via LAN P2P from $hostLanAddress")
                val track = UnifiedTrack(
                    id = "p2p:${contentHash ?: title.hashCode()}",
                    source = SourceType.LOCAL,
                    title = title,
                    artist = artist,
                    streamUri = lanUrl
                )
                return ResolvedTrack(track, ResolvedFrom.P2P_DIRECT)
            }
        }

        // 5. Agro Ephemeral Server Relay
        if (!hostDevice.isNullOrBlank() && secureStorage.agroServerUrl.value != null) {
            val myDevice = secureStorage.agroDeviceId
            val hash = contentHash ?: "stream_${title.hashCode()}"
            val relayStreamUrl = agroRelayClient.openRelayReceiveStream(
                fromDevice = hostDevice.trim(),
                toDevice = myDevice,
                contentHash = hash
            ).getOrNull()

            if (relayStreamUrl != null) {
                Log.i(TAG, "Resolved track via Agro Relay from $hostDevice")
                val track = UnifiedTrack(
                    id = "relay:$hash",
                    source = SourceType.LOCAL,
                    title = title,
                    artist = artist,
                    streamUri = relayStreamUrl
                )
                return ResolvedTrack(track, ResolvedFrom.AGRO_RELAY)
            }
        }

        // 6. Unresolved
        Log.i(TAG, "Track \"$title\" by \"$artist\" could not be resolved from any tier")
        return null
    }

    private fun List<UnifiedTrack>.bestMatch(title: String, artist: String): UnifiedTrack? =
        firstOrNull { candidate ->
            candidate.title.matches(title) && (
                artist.isBlank()
                    || isGenericArtist(artist)
                    || candidate.artist.isBlank()
                    || isGenericArtist(candidate.artist)
                    || candidate.artist.matches(artist)
            )
        }

    private fun String.matches(other: String): Boolean {
        val a = normalise()
        val b = other.normalise()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun String.normalise(): String =
        lowercase()
            .replace(AUDIO_EXTENSIONS, "")
            .replace(BRACKETED, " ")
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(WHITESPACE, " ")

    private fun isGenericArtist(a: String): Boolean {
        val clean = a.trim().lowercase()
        return clean in GENERIC_ARTISTS || clean.startsWith("<") && clean.endsWith(">")
    }

    private companion object {
        const val TAG = "ListenAlongResolver"
        val BRACKETED = Regex("""[\(\[].*?[\)\]]""")
        val WHITESPACE = Regex("""\s+""")
        val AUDIO_EXTENSIONS = Regex("""\.(mp3|flac|wav|ogg|m4a|aac|opus|wma|alac)$""", RegexOption.IGNORE_CASE)
        val GENERIC_ARTISTS = setOf("unknown", "unknown artist", "<unknown>", "various", "various artists", "various artist")
    }
}
