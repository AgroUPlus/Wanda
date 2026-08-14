package com.wander.android.data.sources.ytmusic

/**
 * The client identity an InnerTube request impersonates.
 *
 * YouTube cross-checks the declared client against the request headers and, later, against the
 * fetch of the stream URL it handed out. A stream minted for [ANDROID_MUSIC] and then fetched with
 * a desktop User-Agent is refused, so the identity has to be carried all the way through — see
 * `YTMusicSource.getStreamInfo`, which returns [userAgent] as a playback header.
 */
enum class InnerTubeVariant(
    val clientId: String,
    val clientVersion: String,
    val userAgent: String
) {
    /** Browsing, search and library calls, as the web player makes them. */
    WEB_REMIX(
        clientId = "67",
        clientVersion = "1.20240801.01.00",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    ),

    /** Playback only. Returns direct, un-throttled Opus URLs the web client does not. */
    ANDROID_MUSIC(
        clientId = "21",
        clientVersion = "6.41.52",
        userAgent = "com.google.android.apps.youtube.music/6.41.52 (Linux; U; Android 14) gzip"
    )
}
