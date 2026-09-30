package com.wander.android.data.sources.ytmusic

import androidx.media3.common.MimeTypes
import com.wander.android.data.sources.StreamInfo
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
            // Web variants hand back a scrambled signature rather than a URL, and every variant's
            // URL carries a throttling nonce — both are resolved here.
            val rawUrl = streamUrlResolver.resolve(format, videoId)
            // googlevideo separately checks the PO Token that authorized the /player call which
            // minted this URL, when one was used — it has to travel with the fetch too.
            val url = response.streamingPoToken?.let { "$rawUrl&pot=${URLEncoder.encode(it, "UTF-8")}" }
                ?: rawUrl
            // googlevideo also checks the fetch against the client the URL was minted for, so the
            // media request has to keep the same identity as whichever /player call produced it.
            val mime = format["mimeType"].text()?.substringBefore(';') ?: "audio/webm"
            val bitrate = format["bitrate"].text()?.toIntOrNull()?.div(1000) ?: 160
            StreamInfo(
                uri = url,
                format = mime,
                bitRateKbps = bitrate,
                headers = mapOf("User-Agent" to response.variant.userAgent)
            )
        }
    }
}
