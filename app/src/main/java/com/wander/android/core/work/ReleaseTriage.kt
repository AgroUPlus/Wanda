package com.wander.android.core.work

import com.wander.android.data.sources.agro.AgroArtistRelease

/**
 * The decisions [ArtistReleaseWorker] makes about a batch of releases, kept free of Android so they
 * can be tested on their own.
 */
internal object ReleaseTriage {

    /**
     * Where the next page starts after [page].
     *
     * `updatedAt` is in whole seconds and a batch publish stamps many rows with the same one, so a
     * full page can stop part-way through a second. Resuming strictly after it skipped the rest of
     * that second for good; stepping back one re-reads it, which is harmless because what is
     * announced is decided by recording id. A full page that is all one second has to move on
     * regardless, or it would be read again forever. Same rule as `CatalogSyncRepository`.
     */
    fun nextWatermark(page: List<AgroArtistRelease>, pageSize: Int): Long {
        val newest = page.maxOf { it.updatedAt }
        val full = page.size >= pageSize
        return if (full && page.any { it.updatedAt < newest }) newest - 1 else newest
    }

    /** Who a release is by, for priming. The id when the server sent one, else the name. */
    fun artistKey(release: AgroArtistRelease): String =
        release.artistId.ifBlank { release.artist.trim().lowercase() }

    /**
     * The releases worth saying out loud: those by artists already [primed].
     *
     * `updatedAt` is when the server published a row, not when the record came out. Following an
     * artist makes the server publish their whole back catalogue, which then arrives here looking
     * exactly like news. So the first batch seen for any artist is taken in silently, and only what
     * arrives after it is announced. The cost is one quiet release for an artist followed before
     * the server held anything by them, which is far rarer than a back catalogue.
     *
     * Rows without a recording id are dropped: recorded, one blank id would hide every later one.
     */
    fun announceable(releases: List<AgroArtistRelease>, primed: Set<String>): List<AgroArtistRelease> =
        usable(releases).filter { artistKey(it) in primed }

    /** [releases] with a recording id, each once. */
    fun usable(releases: List<AgroArtistRelease>): List<AgroArtistRelease> =
        releases.filter { it.recordingId.isNotBlank() }.distinctBy { it.recordingId }
}
