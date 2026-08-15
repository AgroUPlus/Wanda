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
 * `playabilityStatus: ERROR` with no formats. So `ANDROID_VR` is the sole playback identity, and
 * `WEB_REMIX` is kept only for the signed-in surfaces — search, browse, library, likes.
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
    )
}
