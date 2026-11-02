package com.wander.android.data.sources.ytmusic

import com.wander.android.core.playback.ReplayGainTags
import kotlinx.serialization.json.JsonObject

/**
 * The ReplayGain 2.0 track gain a `/player` response implies, or null when it carries none.
 *
 * YouTube measures every upload and reports `playerConfig.audioConfig.loudnessDb`: how far the
 * track sits above (positive) or below its own −14 LUFS playback reference — the "content loudness"
 * its stats overlay shows. So the track's integrated loudness is `−14 + loudnessDb`, and the gain
 * that brings it to ReplayGain 2.0's −18 LUFS is `−18 − (−14 + loudnessDb)`, i.e. `−4 − loudnessDb`.
 *
 * Held to the same bounds as a tag (see [ReplayGainTags.plausible]): a value outside them is a
 * broken field, not a quiet song, and is dropped rather than turning a track into silence.
 */
internal fun JsonObject.loudnessGainDb(): Float? {
    val loudness = path("playerConfig", "audioConfig", "loudnessDb").text()?.toFloatOrNull() ?: return null
    return ReplayGainTags.plausible(YOUTUBE_TO_REPLAYGAIN_DB - loudness)
}

/** YouTube's −14 LUFS reference, expressed against ReplayGain 2.0's −18 LUFS. */
private const val YOUTUBE_TO_REPLAYGAIN_DB = -4f
