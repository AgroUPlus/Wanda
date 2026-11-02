package com.wander.android.data.sources.deezer

/**
 * The encodings Deezer's media API serves, best first.
 *
 * The order is the fallback ladder: not every track has every file — plenty of catalogue has no
 * FLAC master — so a request starts at the best format allowed and accepts anything below it.
 */
enum class DeezerAudioFormat(val tag: String, val mimeType: String, val bitRateKbps: Int) {
    FLAC("FLAC", "audio/flac", 1411),
    MP3_320("MP3_320", "audio/mpeg", 320),
    MP3_128("MP3_128", "audio/mpeg", 128);

    companion object {
        fun fromTag(tag: String): DeezerAudioFormat? = entries.firstOrNull { it.tag == tag }

        /**
         * The best format [tier] may stream and [preference] allows, then everything below it.
         *
         * The preference is a ceiling, not an exact pick: a Premium account set to FLAC gets
         * 320 kbps, its own best, rather than dropping to 128. `AUTO` sets no ceiling.
         */
        fun ladderFrom(tier: DeezerAccountTier, preference: String): List<DeezerAudioFormat> {
            val tierBest = when {
                tier.canStreamLossless -> FLAC
                tier.canStreamHq -> MP3_320
                else -> MP3_128
            }
            val ceiling = fromTag(preference) ?: FLAC
            return entries.drop(maxOf(tierBest.ordinal, ceiling.ordinal))
        }
    }
}
