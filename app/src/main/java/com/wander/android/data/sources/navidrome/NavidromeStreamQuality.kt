package com.wander.android.data.sources.navidrome

/**
 * How a Navidrome stream is asked for: the file as stored, or converted down by the server.
 *
 * [ORIGINAL] is the default, because it is the only one that can always seek — see
 * [SubsonicApiClient.buildStreamUrl]. The converted ones trade that for data: the server
 * produces the stream as it sends it, so a seek past what has already arrived may restart the
 * track or be refused, depending on the server.
 *
 * Opus at the lower rates, where it sounds clearly better than MP3 for the same bytes; MP3 at
 * 320, where Opus has nothing left to gain and MP3 is what every server's transcoder ships with.
 * [format] is the Subsonic `format` name, matched against the server's configured transcodings.
 */
enum class NavidromeStreamQuality(val maxBitRateKbps: Int, val format: String?, val mimeType: String) {
    ORIGINAL(0, null, "audio/*"),
    KBPS_320(320, "mp3", "audio/mpeg"),
    KBPS_192(192, "opus", "audio/opus"),
    KBPS_128(128, "opus", "audio/opus");

    companion object {
        /** The stored quality; a key never written, or one from a newer build, streams the original. */
        fun fromKey(key: String?): NavidromeStreamQuality = entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}
