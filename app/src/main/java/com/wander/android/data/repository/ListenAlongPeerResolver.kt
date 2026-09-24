package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.p2p.OffGridTransport
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.StreamInfo
import com.wander.android.data.sources.agro.AgroRelayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ListenAlongPeerResolver"

/**
 * Resolves streams directly from peer devices over LAN, Wi-Fi Direct radio links, or Agro server relay.
 */
@Singleton
internal class ListenAlongPeerResolver @Inject constructor(
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

    suspend fun resolveDirectLan(
        lanAddress: String,
        lanToken: String,
        hash: String,
        title: String,
        artist: String
    ): ResolvedTrack? {
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
        return null
    }

    fun resolveOffGrid(
        hash: String,
        hostTrackId: String?,
        lanToken: String,
        title: String,
        artist: String
    ): ResolvedTrack? {
        val offGridBase = offGrid.connectedBaseUrl() ?: return null
        val offGridToken = ListenAlongResolver.offGridToken(offGrid.grantToken(), lanToken) ?: return null
        if (hash.isBlank() && hostTrackId.isNullOrBlank()) return null

        Log.i(TAG, "Resolved track over a direct radio link with no network involved")
        return encryptedPeerStream(
            base = offGridBase,
            hash = hash,
            trackId = hostTrackId,
            title = title,
            artist = artist,
            grantToken = offGridToken,
            from = ResolvedFrom.P2P_OFFGRID
        )
    }

    suspend fun resolveRelay(
        relayDevice: String,
        hash: String,
        title: String,
        artist: String
    ): ResolvedTrack? {
        val relayStreamUrl = agroRelayClient.openRelayReceiveStream(
            fromDevice = relayDevice,
            toDevice = secureStorage.agroDeviceId,
            contentHash = hash
        ).getOrNull() ?: return null

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

    private fun encryptedPeerStream(
        base: String,
        hash: String,
        title: String,
        artist: String,
        grantToken: String,
        from: ResolvedFrom,
        trackId: String? = null
    ): ResolvedTrack {
        val session = UUID.randomUUID().toString()
        val address = if (hash.isNotBlank()) {
            "hash=" + URLEncoder.encode(hash, "UTF-8")
        } else {
            "track=" + URLEncoder.encode(trackId.orEmpty(), "UTF-8")
        }
        val streamUrl = "$base/p2p/stream?$address&session=$session"
        val track = UnifiedTrack(
            id = "p2p:" + hash.ifBlank { trackId.orEmpty() },
            source = SourceType.LOCAL,
            title = title,
            artist = artist,
            streamUri = streamUrl
        )
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
}
