package com.wander.android.data.sources

import com.wander.android.data.model.SourceType

/**
 * Something that can be given a public link.
 *
 * Sharing used to mean a track and only a track: `createShareLink` took a track id, and
 * `ShareRepository.share` took a `UnifiedTrack`. Handing someone a record or a playlist you made
 * is at least as common a thing to want to do, and there was no way to express it.
 *
 * [id] is the namespaced id the source already uses (`navidrome:al-42`, `ytm:MPREb_…`), so a
 * source strips its own prefix exactly as it does everywhere else.
 */
data class ShareTarget(
    val kind: ShareKind,
    val source: SourceType,
    val id: String,
    /** What the link is of, for the share sheet's own text and for Navidrome's share description. */
    val title: String,
    val subtitle: String? = null
) {
    /** "Kid A — Radiohead", or just the title when there is nothing to qualify it with. */
    val description: String
        get() = subtitle?.takeIf { it.isNotBlank() }?.let { "$title — $it" } ?: title
}

enum class ShareKind { TRACK, ALBUM, ARTIST, PLAYLIST }
