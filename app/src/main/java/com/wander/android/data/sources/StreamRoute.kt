package com.wander.android.data.sources

/**
 * Which tier of `PlaybackStreamResolver` produced a [StreamInfo]: where the audio is actually
 * coming from, as opposed to which source the track belongs to.
 */
enum class StreamRoute {
    /** A file downloaded to this device. */
    DOWNLOAD,

    /** A local copy of the same recording, found by title. */
    LOCAL_COPY,

    /** The user's Navidrome server, substituted for the original source. */
    NAVIDROME,

    /** The track's own backend (YouTube Music, Deezer, a local file…). */
    SOURCE,

    /** A one-off stream registered for a single track, such as a peer's. */
    EPHEMERAL,

    /** A podcast episode's enclosure URL. */
    PODCAST
}
