package com.wander.android.data.sources.ytmusic

/**
 * The client identity an InnerTube request impersonates.
 *
 * YouTube cross-checks the declared client against the request headers and, later, against the
 * fetch of the stream URL it handed out. A stream minted for one variant and then fetched with a
 * different User-Agent is refused, so the identity has to be carried all the way through — see
 * `YTMusicSource.getStreamInfo`, which returns [userAgent] as a playback header.
 *
 * `ANDROID_MUSIC` and `WEB_REMIX` player calls now require a PO Token (BotGuard/DroidGuard
 * attestation) YouTube does not hand out to third-party apps; without one, `/player` refuses even
 * playable, unrestricted videos with LOGIN_REQUIRED/UNPLAYABLE. `WEB_EMBEDDED` returns
 * `playabilityStatus: ERROR` with no formats. So `ANDROID_VR` is the sole playback identity for
 * ordinary tracks, and `WEB_REMIX` is kept only for the signed-in surfaces — search, browse,
 * library, likes.
 *
 * Livestreams are the exception, and [VISIONOS] exists for them alone — see its own note.
 */
enum class InnerTubeVariant(
    /** `X-YouTube-Client-Name` header value / INNERTUBE_CONTEXT_CLIENT_NAME. */
    val clientId: String,
    /** `context.client.clientName` body value. */
    val contextClientName: String,
    val clientVersion: String,
    val userAgent: String,
    /**
     * InnerTube host this identity is allowed to talk to. Only the music web client belongs on
     * `music.youtube.com`; the embed and headset identities are plain YouTube clients and the
     * music host answers them inconsistently.
     */
    val apiBaseUrl: String
) {
    /** Browsing, search and library calls, as the web player makes them. */
    WEB_REMIX(
        clientId = "67",
        contextClientName = "WEB_REMIX",
        clientVersion = "1.20260707.12.00",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        apiBaseUrl = "https://music.youtube.com/youtubei/v1"
    ),

    /**
     * Playback, and the only client used for it: the Quest headset identity. PO-Token-exempt, and
     * its formats carry a plain `url` rather than the `signatureCipher` the web identities return.
     */
    ANDROID_VR(
        clientId = "28",
        contextClientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        apiBaseUrl = "https://www.youtube.com/youtubei/v1"
    ),

    /**
     * Livestreams, and nothing else.
     *
     * A live video has no format list at all — it is served through an HLS manifest, and
     * `streamingData.hlsManifestUrl` is the only thing in a `/player` response that can play it.
     * [ANDROID_VR] is a headset identity that never returns that field, and [WEB_REMIX] refuses
     * the call outright without a PO Token, so between them a livestream had no route to a
     * manifest and every one of them failed with a flat "will not play this track".
     *
     * This replaced the `IOS` handset identity, which was the actual cause of "Stream expired.
     * Play it again to refresh it." iOS is the *one* client for which YouTube requires a PO Token
     * on live HLS specifically — it hands out a manifest that plays for about thirty seconds and
     * then answers every further segment with 403, which is exactly what the player saw. No amount
     * of carrying the client identity onto the segment requests could fix that; the token was
     * never the User-Agent. (yt-dlp encodes the same rule: every client is exempt from a PO Token
     * on HLS *except* iOS.)
     *
     * The Vision Pro identity has no PO Token policy at all, needs no signature descrambling, and
     * its manifests were verified serving segments continuously for three minutes with no headers
     * of any kind on the media requests. It does insist on a real visitor session — with none it
     * answers LOGIN_REQUIRED — which is what `InnerTubeClient.visitorSession()` is for.
     */
    VISIONOS(
        clientId = "101",
        contextClientName = "VISIONOS",
        clientVersion = "1.02",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        apiBaseUrl = "https://www.youtube.com/youtubei/v1"
    )
}
