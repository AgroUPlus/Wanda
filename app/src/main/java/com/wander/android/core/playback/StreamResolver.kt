package com.wander.android.core.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.wander.android.data.model.isOneShotTrackId
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

        // Remembered for any YouTube stream (manifest, segments, ranges). Dropped as soon as a
        // non-YouTube track resolves, so one source's identity is never sent with another's.
        val host = streamInfo.uri.toUri().host
        liveHeaders = if (carriesLiveIdentity(host)) {
            streamInfo.headers
        } else {
            emptyMap()
        }

        return dataSpec
            .buildUpon()
            .setUri(streamInfo.uri.toUri())
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + streamInfo.headers)
            // A borrowed track is never cached, and this is where that gets decided.
            //
            // `RelayDecryptingDataSource` documents the spec as being marked uncacheable, and it
            // was not — no cache flag appeared anywhere in the project. Two consequences, both
            // real. `CacheDataSource` re-opens its upstream to fill each span, and a relay session
            // serves its receiving half exactly once, so the second open came back `409`. And a
            // `Tee` wrote somebody else's track to this device's disk, which is precisely what the
            // placement of the decrypting source exists to prevent.
            //
            // The flag reads oddly and is exactly right: both peer tiers and the relay omit
            // `Content-Length` on purpose, because encryption framing makes the byte count differ
            // from the file's and neither stream is seekable. Length is therefore always unknown
            // for these, and this is the flag Media3 offers for "then do not cache it".
            .setFlags(
                dataSpec.flags or
                    if (isOneShotTrackId(trackId)) DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN else 0
            )
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
