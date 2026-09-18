package com.wander.android.core.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * What the decoder is *actually* producing, as opposed to [com.wander.android.data.model.UnifiedTrack.audioQualityLabel]
 * — which is a guess from static container tags a backend reported, and can be wrong: a Navidrome
 * track tagged FLAC still gets transcoded to Opus if the server's transcoding rules say so, and
 * the badge would keep claiming lossless.
 *
 * Captured in [PlaybackService] off the real Media3 [androidx.media3.exoplayer.analytics.AnalyticsListener]
 * callback, then carried to [PlayerConnection] via [android.media.session.MediaSession] extras —
 * the one channel a [androidx.media3.session.MediaController] has for state that isn't part of the
 * `Player` interface itself.
 */
data class ActualAudioFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int?,
    val bitrateKbps: Int?,
    val mimeType: String?,
    val isLossless: Boolean
) {

    fun toBundle(): Bundle = Bundle().apply {
        putInt(KEY_SAMPLE_RATE, sampleRateHz)
        putInt(KEY_CHANNELS, channelCount)
        bitDepth?.let { putInt(KEY_BIT_DEPTH, it) }
        bitrateKbps?.let { putInt(KEY_BITRATE, it) }
        mimeType?.let { putString(KEY_MIME_TYPE, it) }
        putBoolean(KEY_LOSSLESS, isLossless)
    }

    @OptIn(UnstableApi::class)
    companion object {
        private const val KEY_SAMPLE_RATE = "wanda.audioFormat.sampleRateHz"
        private const val KEY_CHANNELS = "wanda.audioFormat.channelCount"
        private const val KEY_BIT_DEPTH = "wanda.audioFormat.bitDepth"
        private const val KEY_BITRATE = "wanda.audioFormat.bitrateKbps"
        private const val KEY_MIME_TYPE = "wanda.audioFormat.mimeType"
        private const val KEY_LOSSLESS = "wanda.audioFormat.isLossless"

        private val LOSSLESS_MIME_TYPES = setOf(MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_WAV)

        fun of(format: androidx.media3.common.Format): ActualAudioFormat {
            val mime = format.sampleMimeType
            return ActualAudioFormat(
                sampleRateHz = format.sampleRate.takeIf { it > 0 } ?: 0,
                channelCount = format.channelCount.takeIf { it > 0 } ?: 0,
                bitDepth = format.pcmEncodingBitDepth().takeIf { it > 0 },
                bitrateKbps = format.averageBitrate.takeIf { it > 0 }?.let { it / 1000 },
                mimeType = mime,
                isLossless = mime in LOSSLESS_MIME_TYPES
            )
        }

        /** Reads a [Bundle] written by [toBundle], or null when it carries none of these keys. */
        fun fromBundle(bundle: Bundle): ActualAudioFormat? {
            if (!bundle.containsKey(KEY_SAMPLE_RATE)) return null
            return ActualAudioFormat(
                sampleRateHz = bundle.getInt(KEY_SAMPLE_RATE),
                channelCount = bundle.getInt(KEY_CHANNELS),
                bitDepth = bundle.getInt(KEY_BIT_DEPTH, -1).takeIf { it > 0 },
                bitrateKbps = bundle.getInt(KEY_BITRATE, -1).takeIf { it > 0 },
                mimeType = bundle.getString(KEY_MIME_TYPE),
                isLossless = bundle.getBoolean(KEY_LOSSLESS, false)
            )
        }

        private fun androidx.media3.common.Format.pcmEncodingBitDepth(): Int = when (pcmEncoding) {
            androidx.media3.common.C.ENCODING_PCM_8BIT -> 8
            androidx.media3.common.C.ENCODING_PCM_16BIT, androidx.media3.common.C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
            androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
    }
}
