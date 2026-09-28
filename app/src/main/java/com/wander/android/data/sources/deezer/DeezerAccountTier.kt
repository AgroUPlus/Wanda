package com.wander.android.data.sources.deezer

/**
 * Account tier for Deezer, which determines the maximum allowed audio streaming format.
 *
 * Requesting 320 kbps or FLAC with a free account returns 403 or license errors from Deezer CDN.
 * Auto-detected upon login via `deezer.getUserData`.
 */
enum class DeezerAccountTier(
    val formatTag: String,
    val defaultBitrateKbps: Int
) {
    FREE("MP3_128", 128),
    PREMIUM("MP3_320", 320),
    HIFI("FLAC", 1411);

    val canStreamLossless: Boolean get() = this == HIFI
    val canStreamHq: Boolean get() = this == PREMIUM || this == HIFI

    companion object {
        fun fromFlags(isLossless: Boolean, isHq: Boolean): DeezerAccountTier = when {
            isLossless -> HIFI
            isHq -> PREMIUM
            else -> FREE
        }

        fun fromString(value: String): DeezerAccountTier =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FREE
    }
}
