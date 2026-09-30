package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.repository.ListenAlongMatcher.bestMatch
import javax.inject.Inject
import javax.inject.Singleton

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
 */
@Singleton
internal class ListenAlongResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val secureStorage: SecureStorage,
    private val peerResolver: ListenAlongPeerResolver
) {
    suspend fun resolve(
        title: String,
        artist: String,
        hostDevice: String? = null,
        hostLanAddress: String? = null,
        hostLanToken: String? = null,
        contentHash: String? = null,
        hostTrackId: String? = null
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

        val lanAddress = hostLanAddress?.trim().orEmpty()
        val lanToken = hostLanToken?.trim().orEmpty()
        val hash = contentHash?.trim().orEmpty()

        // 4. Direct LAN P2P
        if (canTryDirect(lanAddress, lanToken, hash)) {
            val direct = peerResolver.resolveDirectLan(lanAddress, lanToken, hash, title, artist)
            if (direct != null) return direct
        }

        // 5. Off-grid direct radio link
        val offGrid = peerResolver.resolveOffGrid(hash, hostTrackId, lanToken, title, artist)
        if (offGrid != null) return offGrid

        // 6. Agro relay
        val relayDevice = hostDevice?.trim().orEmpty()
        if (canTryRelay(relayDevice, hash, secureStorage.agroServerUrl.isNotBlank())) {
            val relay = peerResolver.resolveRelay(relayDevice, hash, title, artist)
            if (relay != null) return relay
        }

        // 7. Unresolved
        Log.i(TAG, "Track \"$title\" by \"$artist\" could not be resolved from any tier")
        return null
    }

    internal companion object {
        const val TAG = "ListenAlongResolver"

        /**
         * Whether a direct transfer over the local network is worth attempting.
         */
        fun canTryDirect(address: String?, token: String?, contentHash: String?): Boolean =
            !address.isNullOrBlank() && !token.isNullOrBlank() && !contentHash.isNullOrBlank()

        /**
         * Which bearer the off-grid tier should present, or null if it has none.
         */
        fun offGridToken(peerGrant: String?, agroToken: String?): String? =
            peerGrant?.takeIf { it.isNotBlank() } ?: agroToken?.takeIf { it.isNotBlank() }

        /**
         * Whether the relay is worth attempting.
         */
        fun canTryRelay(
            hostDevice: String?,
            contentHash: String?,
            serverConfigured: Boolean
        ): Boolean =
            !hostDevice.isNullOrBlank() && !contentHash.isNullOrBlank() && serverConfigured
    }
}
