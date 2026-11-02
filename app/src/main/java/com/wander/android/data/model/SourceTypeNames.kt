package com.wander.android.data.model

import androidx.annotation.StringRes
import com.wander.android.R

/**
 * The name a source goes by on screen. [SourceType.displayName] is the fixed English one, for
 * logs and error diagnostics; anything the user reads goes through here so it can be translated.
 */
@get:StringRes
val SourceType.nameRes: Int
    get() = when (this) {
        SourceType.LOCAL -> R.string.source_name_local
        SourceType.NAVIDROME -> R.string.source_name_navidrome
        SourceType.YTMUSIC -> R.string.source_name_ytmusic
        SourceType.DEEZER -> R.string.source_name_deezer
        SourceType.PODCAST -> R.string.source_name_podcast
        SourceType.AGRO -> R.string.source_name_agro
        SourceType.UNRESOLVED -> R.string.source_name_unresolved
    }

/**
 * The name for places that are tight for room — the Home filter row, where four chips have to fit
 * on one line. The full [nameRes] unless that genuinely does not fit.
 */
@get:StringRes
val SourceType.shortNameRes: Int
    get() = when (this) {
        SourceType.LOCAL -> R.string.source_short_name_local
        SourceType.AGRO -> R.string.source_short_name_agro
        else -> nameRes
    }
