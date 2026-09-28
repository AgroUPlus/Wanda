package com.wander.android.data.repository

import com.wander.android.data.model.ArtistDetails

/**
 * What came back from asking a backend for its artist page — see [CatalogRepository.artistDetails].
 *
 * Split from a bare nullable so the screen (through `ArtistCatalogLoader`) can tell "nothing here,
 * as expected" apart from "something went wrong fetching it", which used to be the same `null` and
 * left every real network failure looking identical to an artist with no backend page at all.
 */
sealed interface ArtistFetch {
    data class Found(val page: ArtistDetails) : ArtistFetch
    /** No source could be asked, or the page fetched was rejected as being about someone else. */
    data object NotFound : ArtistFetch
    data class Failed(val error: Throwable) : ArtistFetch
}
