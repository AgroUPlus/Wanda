package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Full HD: sharp even filling the screen in the immersive player, and still hardware-decoded. */
internal const val MAX_CLIP_HEIGHT = 1080

/**
 * The video-only stream to show as the cover while a clip plays, or null when there is none.
 *
 * Video-only, never a muxed format: the audio already plays through the playback service, so a
 * second audio stream would only be downloaded to be thrown away. H.264 first because every phone
 * decodes it in hardware; VP9 after it, since some only decode that in software. Neither above
 * [MAX_CLIP_HEIGHT] unless nothing smaller exists, in which case the smallest is taken.
 *
 * Does not judge `playabilityStatus` — [bestAudioFormat] runs first on the same response and has
 * already thrown with the reason if the video cannot be played at all.
 */
internal fun JsonObject.bestVideoFormat(): JsonObject? {
    val video = path("streamingData", "adaptiveFormats")?.array()
        ?.map { it.jsonObject }
        .orEmpty()
        .filter { it["mimeType"].text()?.startsWith("video/") == true && it.hasPlayableSource() }
    val fitting = video.filter { it.height() <= MAX_CLIP_HEIGHT }
    return fitting.filter { it.codecs().startsWith("avc1") }.maxByOrNull { it.height() }
        ?: fitting.filter { it.codecs().startsWith("vp9") || it.codecs().startsWith("vp09") }.maxByOrNull { it.height() }
        ?: fitting.maxByOrNull { it.height() }
        ?: video.minByOrNull { it.height() }
}

private fun JsonObject.height(): Int = this["height"].text()?.toIntOrNull() ?: Int.MAX_VALUE

private fun JsonObject.codecs(): String =
    this["mimeType"].text()?.substringAfter("codecs=\"", "")?.substringBefore('"').orEmpty()
