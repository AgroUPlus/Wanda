package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a `wanda://track/<id>` placeholder into the real stream URL and headers, at the moment
 * the loader opens it. Runs on ExoPlayer's loading thread, so blocking here is correct.
 *
 * A progressive track is one request and one resolve, so it needs nothing more than that. A
 * livestream is not: the placeholder resolves to an HLS *manifest*, and playing it means fetching
 * the media playlist and then a segment every few seconds — all at absolute URLs taken out of the
 * manifest, none of which look like `wanda://` and none of which used to be touched here.
 *
 * That is what made livestreams fail with "Stream expired". YouTube cross-checks the client
 * identity a stream was minted for against the identity that later fetches it — the reason
 * `InnerTubeVariant` carries a User-Agent through to playback at all — and the follow-up requests
 * were arriving with none of it, so YouTube refused them with a 403. The manifest itself was fine,
 * which is exactly why the track was detected as live and then would not play.
 *
 * So the identity is remembered for as long as a live stream is the thing being played, and
 * applied to the requests that manifest goes on to make. Only to those: it is scoped to YouTube's
 * own hosts, and dropped the moment an ordinary track resolves.
 */
@Singleton
@OptIn(UnstableApi::class)
class StreamResolver @Inject constructor(
    private val musicRepository: MusicRepository
) : ResolvingDataSource.Resolver {

    /**
     * Headers the live manifest currently playing was minted for.
     *
     * Written on the resolve of a manifest and read on every request that follows it, from
     * ExoPlayer's loader threads — hence volatile. Empty whenever what is playing is not live,
     * which is what keeps one track's identity from being sent along with another's.
     */
    @Volatile
    private var liveHeaders: Map<String, String> = emptyMap()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val trackId = dataSpec.uri.wandaTrackId() ?: return carryLiveIdentity(dataSpec)

        val streamInfo = runBlocking { musicRepository.getStreamInfo(trackId) }
            .getOrElse { throw IOException("Could not resolve stream for track", it) }

        // Remembered only for a manifest. Everything else is a single self-contained request, and
        // holding an identity past it would mean sending it with whatever played next.
        liveHeaders = if (streamInfo.format == MimeTypes.APPLICATION_M3U8) {
            streamInfo.headers
        } else {
            emptyMap()
        }

        return dataSpec
            .buildUpon()
            .setUri(streamInfo.uri.toUri())
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + streamInfo.headers)
            .build()
    }

    /** A media playlist or a segment, fetched at an absolute URL out of the live manifest. */
    private fun carryLiveIdentity(dataSpec: DataSpec): DataSpec {
        val headers = liveHeaders
        if (headers.isEmpty() || !carriesLiveIdentity(dataSpec.uri.host)) return dataSpec

        return dataSpec
            .buildUpon()
            // The spec's own headers win: this is filling in an identity, not overriding a
            // request that already states one.
            .setHttpRequestHeaders(headers + dataSpec.httpRequestHeaders)
            .build()
    }
}

/**
 * Whether a host is one of YouTube's, and so one the live identity belongs to.
 *
 * Matched on the registrable domain rather than on the manifest's own host, because they differ:
 * a manifest is served from `manifest.googlevideo.com` and its segments from numbered siblings.
 * Suffix-matched with the leading dot so a lookalike domain — `notgooglevideo.com` — cannot
 * collect headers meant for YouTube.
 */
internal fun carriesLiveIdentity(host: String?): Boolean {
    val name = host?.lowercase() ?: return false
    return YOUTUBE_DOMAINS.any { name == it || name.endsWith(".$it") }
}

private val YOUTUBE_DOMAINS = listOf("googlevideo.com", "youtube.com", "ytimg.com")
