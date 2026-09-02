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
