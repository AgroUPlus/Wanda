package com.wander.android.data.model

/**
 * [UnifiedTrack.extraData] keys for the ReplayGain a *source* reports alongside a track, in dB —
 * OpenSubsonic's song-level `replayGain`. Playback uses them where the stream's own tags say
 * nothing, as a transcoded stream's do not.
 *
 * Extras rather than fields, for the reason `MediaStoreScanner` gives: every queued `MediaItem`
 * carries the serialised model, and most tracks have no such values.
 */
const val EXTRA_REPLAYGAIN_TRACK_DB = "replayGainTrackDb"
const val EXTRA_REPLAYGAIN_ALBUM_DB = "replayGainAlbumDb"
