package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.p2p.OffGridTransport
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.StreamInfo
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

    /**
     * Streamed over a direct radio link with no router involved at all — a car, a plane, a
     * festival. Distinct from [P2P_DIRECT] because the guarantee is different: that one needs a
     * network both devices are already on, this one needs nothing.
     */
    P2P_OFFGRID,

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
 * 5. Off-grid direct radio link — Wi-Fi Direct, no router (port 8702 again)
 * 6. Agro Ephemeral Relay audio chunks
 * 7. Unresolved (null)
 *
 * Tier 5 sits below the LAN and above the relay on purpose. It is strictly better than the relay —
 * nothing leaves the two devices, and it is an order of magnitude faster — but it costs a radio
 * link and, on most devices, a tap on a system dialog, so it must not be attempted while a network
 * both devices are already on would have done.
 */
@Singleton
internal class ListenAlongResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val agroRelayClient: AgroRelayClient,
    private val secureStorage: SecureStorage,
    private val offGrid: OffGridTransport,
    private val identityKeyManager: IdentityKeyManager
) {
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolve(
        title: String,
        artist: String,
        hostDevice: String? = null,
        hostLanAddress: String? = null,
        hostLanToken: String? = null,
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

        // 4. Direct transfer over the local network.
        //
        // Needs all three: somewhere to connect (the server only supplies an address when it
        // judged both devices to be on one network), a grant to present when connecting, and a
        // hash naming which bytes to ask for. A title is not enough here — the host's server
        // answers for files, not for names.
        //
        // `hostLanAddress` already carries its port: it is the `host:port` the host's device
        // reported to Agro, which validates the shape before storing it.
        // Normalised once so the gate and the request below agree on what "present" means.
        val lanAddress = hostLanAddress?.trim().orEmpty()
        val lanToken = hostLanToken?.trim().orEmpty()
        val hash = contentHash?.trim().orEmpty()

        if (canTryDirect(lanAddress, lanToken, hash)) {
            val base = "http://$lanAddress"
            val isLanAlive = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url("$base/p2p/ping").build()
                    probeClient.newCall(req).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
            if (isLanAlive) {
                Log.i(TAG, "Resolved track over the local network from the host's device")
                return encryptedPeerStream(base, hash, title, artist, lanToken, ResolvedFrom.P2P_DIRECT)
            }
        }

        // 5. An off-grid radio link, when there is no shared network to have used.
        //
        // Only when a link is already up. Forming one takes a system dialog and up to half a
        // minute, and a resolver is called while a listener is waiting for audio — raising a radio
        // link from here would look like the app having frozen. The user starts the link; this
        // tier notices that they did.
        //
        // The grant comes from the *peer*, not from Agro. That is the whole point of this tier:
        // two devices with no server between them still have to authorise each other, and
        // `OffGridPairing` is where that happened when the link was raised. Requiring Agro's token
        // here — as this did — meant the one tier designed to work without a server could not.
        // Agro's token is still accepted, for a link raised while the account happened to be
        // online.
        if (hash.isNotBlank()) {
            val offGridBase = offGrid.connectedBaseUrl()
            val offGridToken = offGridToken(offGrid.grantToken(), lanToken)
            if (offGridBase != null && offGridToken != null) {
                Log.i(TAG, "Resolved track over a direct radio link with no network involved")
                return encryptedPeerStream(
                    offGridBase, hash, title, artist, offGridToken, ResolvedFrom.P2P_OFFGRID
                )
            }
        }

        // 6. Through Agro's relay, when a direct connection is not on offer.
        //
        // Also needs a real hash: the relay asks the host's device for specific bytes, and a hash
        // invented from the title would name a file nobody has. Without one there is nothing left
        // to try, which is what tier 6 says.
        val relayDevice = hostDevice?.trim().orEmpty()
        if (canTryRelay(relayDevice, hash, secureStorage.agroServerUrl.isNotBlank())) {
            val relayStreamUrl = agroRelayClient.openRelayReceiveStream(
                fromDevice = relayDevice,
                toDevice = secureStorage.agroDeviceId,
                contentHash = hash
            ).getOrNull()

            if (relayStreamUrl != null) {
                Log.i(TAG, "Resolved track through the Agro relay")
                val track = UnifiedTrack(
                    id = "relay:$hash",
                    source = SourceType.LOCAL,
                    title = title,
                    artist = artist,
                    streamUri = relayStreamUrl
                )
                musicRepository.registerEphemeralStream(
                    track.id,
                    StreamInfo(
                        uri = relayStreamUrl,
                        headers = mapOf(
                            "Authorization" to "Bearer ${secureStorage.agroApiKey}"
                        )
                    )
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


    /**
     * A peer's track, encrypted end to end, over whichever link reached it.
     *
     * One method for both peer tiers because the difference between them is which radio carried the
     * bytes, and nothing above the socket should care. The session id is fresh per stream and goes
     * in the URL, where the decrypting source reads it back off the URL it actually fetched — so
     * the two ends cannot disagree about which key derivation they meant.
     *
     * This device's identity public key goes up with the request, and the peer seals the audio key
     * to it. Before this, the LAN tier sent the audio in the clear while the far slower relay tier
     * encrypted it, which is the wrong way round: the network your phone is sharing with strangers
     * is the one that needs it.
     */
    private fun encryptedPeerStream(
        base: String,
        hash: String,
        title: String,
        artist: String,
        grantToken: String,
        from: ResolvedFrom
    ): ResolvedTrack {
        val session = java.util.UUID.randomUUID().toString()
        val streamUrl = "$base/p2p/stream?hash=$hash&session=$session"
        val track = UnifiedTrack(
            id = "p2p:$hash",
            source = SourceType.LOCAL,
            title = title,
            artist = artist,
            streamUri = streamUrl
        )
        // Both secrets travel as headers, registered for this id only. Neither is put on the track:
        // a bearer token has no business in a model that gets serialised into a MediaItem and handed
        // across IPC.
        musicRepository.registerEphemeralStream(
            track.id,
            StreamInfo(
                uri = streamUrl,
                headers = mapOf(
                    "Authorization" to "Bearer $grantToken",
                    "X-Wanda-Identity" to identityKeyManager.getPublicKeyBase64()
                )
            )
        )
        return ResolvedTrack(track, from)
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

    internal companion object {
        const val TAG = "ListenAlongResolver"

        /**
         * Whether a direct transfer over the local network is worth attempting.
         *
         * All three or none. The address says the server judged the two devices to share a
         * network, the token is what the peer's server will actually check, and the hash names
         * which bytes to ask for — a peer answers for files, not for titles. Missing any one of
         * them means the tier cannot work, and trying anyway costs a timeout on every track.
         */
        fun canTryDirect(address: String?, token: String?, contentHash: String?): Boolean =
            !address.isNullOrBlank() && !token.isNullOrBlank() && !contentHash.isNullOrBlank()

        /**
         * Whether the relay is worth attempting.
         *
         * No LAN address needed — that is the point of the relay — but still a device to address
         * and a hash to ask for. A hash invented from the title would name a file nobody has, so
         * its absence is a real answer: there is nothing to transfer, only a name to match.
         */
        /**
         * Which bearer the off-grid tier should present, or null if it has none.
         *
         * The peer's own grant comes first, and it is the only one that exists in the case this
         * tier was built for: two devices with no server between them. Agro's token is accepted
         * behind it, for a radio link raised while the account happened to be online — but
         * requiring it, as this once did, made the one tier designed to work without a server
         * refuse to work without a server.
         */
        fun offGridToken(peerGrant: String?, agroToken: String?): String? =
            peerGrant?.takeIf { it.isNotBlank() } ?: agroToken?.takeIf { it.isNotBlank() }

        fun canTryRelay(
            hostDevice: String?,
            contentHash: String?,
            serverConfigured: Boolean
        ): Boolean =
            !hostDevice.isNullOrBlank() && !contentHash.isNullOrBlank() && serverConfigured
        val BRACKETED = Regex("""[\(\[].*?[\)\]]""")
        val WHITESPACE = Regex("""\s+""")
        val AUDIO_EXTENSIONS = Regex("""\.(mp3|flac|wav|ogg|m4a|aac|opus|wma|alac)$""", RegexOption.IGNORE_CASE)
        val GENERIC_ARTISTS = setOf("unknown", "unknown artist", "<unknown>", "various", "various artists", "various artist")
    }
}
