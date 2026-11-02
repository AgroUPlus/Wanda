package com.wander.android.data.model

/**
 * The artist label used when a source provides no artist name.
 *
 * A named constant rather than a repeated literal for two reasons:
 * - A future rename touches one line instead of eight.
 * - Callers that need to filter or rewrite these rows can guard against the constant rather than
 *   a bare string, which would break silently if the spelling changed.
 */
const val UNKNOWN_ARTIST = "Unknown Artist"

/**
 * The `format` of a Navidrome internet-radio station whose stream is an HLS manifest.
 *
 * A station is whatever its broadcaster runs — often a plain Icecast or Shoutcast stream, which
 * playback must hand to the progressive extractors rather than parse as a playlist. Playback picks
 * the container before the stream URL is even resolved, so the station's track has to say which
 * it is; see `isHlsLive` in `MediaItems`.
 */
const val HLS_LIVE_FORMAT = "HLS"
