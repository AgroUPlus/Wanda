package com.wander.android.data.sources.ytmusic

import androidx.media3.common.MimeTypes
import com.wander.android.data.sources.StreamInfo
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves playable stream URLs and headers for YouTube Music tracks.
 */
@Singleton
class YTMusicStreamResolver @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val streamUrlResolver: StreamUrlResolver
) {
    suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.player(videoId).mapCatching { response ->
            // A livestream is the manifest and nothing else: there is no signature to unscramble
            // and no throttling nonce, and appending a PO Token to a manifest URL invalidates it.
            response.hlsManifestUrl?.let { manifest ->
                return@mapCatching StreamInfo(
                    uri = manifest,
                    format = MimeTypes.APPLICATION_M3U8,
                    bitRateKbps = 0,
                    headers = mapOf("User-Agent" to response.variant.userAgent)
                )
            }
            val format = response.format
                ?: throw IOException("YouTube Music returned no playable audio for this track")
            val mime = format["mimeType"].text()?.substringBefore(';') ?: "audio/webm"
            val bitrate = format["bitrate"].text()?.toIntOrNull()?.div(1000) ?: 160
            signedStream(response, format, videoId, mime, bitrate)
        }
    }

    /**
     * The video-only stream of [trackId], for the clip shown in place of the cover. Fails rather
     * than handing back nothing when YouTube offers no video track for this id — a song upload
     * has none — so the player keeps the cover and says why.
     */
    suspend fun getVideoStreamInfo(trackId: String): Result<StreamInfo> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.player(videoId).mapCatching { response ->
            val format = response.videoFormat
                ?: throw IOException("YouTube Music has no video for this track")
            val mime = format["mimeType"].text()?.substringBefore(';') ?: "video/mp4"
            val bitrate = format["bitrate"].text()?.toIntOrNull()?.div(1000) ?: 0
            signedStream(response, format, videoId, mime, bitrate)
        }
    }

    private suspend fun signedStream(
        response: PlayerResponse,
        format: JsonObject,
        videoId: String,
        mime: String,
        bitrateKbps: Int
    ): StreamInfo {
        // Web variants hand back a scrambled signature rather than a URL, and every variant's
        // URL carries a throttling nonce — both are resolved here.
        val rawUrl = streamUrlResolver.resolve(format, videoId)
        // googlevideo separately checks the PO Token that authorized the /player call which
        // minted this URL, when one was used — it has to travel with the fetch too.
        val url = response.streamingPoToken?.let { "$rawUrl&pot=${URLEncoder.encode(it, "UTF-8")}" }
            ?: rawUrl
        // googlevideo also checks the fetch against the client the URL was minted for, so the
        // media request has to keep the same identity as whichever /player call produced it.
        return StreamInfo(
            uri = url,
            format = mime,
            bitRateKbps = bitrateKbps,
            headers = mapOf("User-Agent" to response.variant.userAgent)
        )
    }
}
