package com.wander.android.data.model

/**
 * One backend's answer to a search: the songs it matched, and the records and artists besides.
 *
 * Albums and artists are only ever what the backend itself returned for the query — see
 * `SourceCapabilities.collectionSearch`. A backend that can only search songs answers with
 * [tracks] alone, rather than with records guessed from the songs it found.
 */
data class SearchCatalog(
    val tracks: List<UnifiedTrack> = emptyList(),
    val albums: List<UnifiedAlbum> = emptyList(),
    val artists: List<UnifiedArtist> = emptyList()
)
