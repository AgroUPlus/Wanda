package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/**
 * Tells two artists who share a name apart.
 *
 * Room finds an artist's work with `WHERE artist = :name COLLATE NOCASE`, and that is not a
 * mistake — one artist genuinely reaches Room capitalised differently by different backends, so an
 * exact match would split their discography in half. The cost is that two *different* artists whose
 * names differ only in case become one: a Japanese singer called "misa" and an unrelated "MISA"
 * arrive as the same page, with each other's songs on it.
 *
 * The name cannot settle it, so identity does. Where a backend published an artist id — YouTube
 * Music always does — an item carrying a *different* id is definitely somebody else and is dropped.
 *
 * What is deliberately **not** done is preferring an exact-case name match when no ids are
 * available. It would fix the same-name case for sources that publish no ids, and it would break
 * the case this query was written for, silently hiding the half of a discography that a second
 * backend spelled differently. An item we cannot disprove is kept; a page with a stranger's song on
 * it is a smaller failure than a page missing the user's own music.
 */
internal object ArtistIdentity {

    /**
     * Discovers all backend ids that refer to this artist, by seeing which of their tracks
     * deduplicate against one another across sources.
     */
    fun aliasesOf(
        tracks: List<UnifiedTrack>,
        pageArtistId: String?
    ): Set<String> {
        val groups = TrackDeduplicator.groupRecordings(tracks)
        val aliases = mutableSetOf<String>()
        if (pageArtistId != null) {
            aliases.add(pageArtistId)
        } else {
            val firstId = tracks.firstNotNullOfOrNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
            if (firstId != null) aliases.add(firstId)
        }
        if (aliases.isEmpty()) return emptySet()

        var added = true
        while (added) {
            added = false
            for (group in groups) {
                val groupIds = group.mapNotNull { it.artistId?.takeIf { id -> id.isNotBlank() } }
                if (groupIds.any { it in aliases }) {
                    val newIds = groupIds.filterNot { it in aliases }
                    if (newIds.isNotEmpty()) {
                        aliases.addAll(newIds)
                        added = true
                    }
                }
            }
        }
        return aliases
    }

    /**
     * Keeps items that could belong to this artist.
     *
     * [aliases] empty means nothing is known about identity and everything is kept.
     * [idOf] returning null likewise means "cannot tell", never "different".
     */
    fun <T> sameArtist(items: List<T>, aliases: Set<String>, idOf: (T) -> String?): List<T> {
        if (aliases.isEmpty()) return items
        return items.filter { item ->
            val id = idOf(item)
            id.isNullOrBlank() || id in aliases
        }
    }
}
