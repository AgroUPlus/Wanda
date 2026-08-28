package com.wander.android.data.repository

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.ArtistPage
import com.wander.android.data.model.ArtistSection
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack

/**
 * Folds the backend's artist page together with what the library knows into one [ArtistPage].
 *
 * Pure: no Android types, no coroutines, no injection. Every interesting decision on the artist
 * screen lives here so it can be tested without a device.
 *
 * **On classifying by heading.** Buckets are chosen from the shelf's title, matched against a
 * small English word list. That is genuinely fragile — YouTube Music localises its headings, so a
 * French page's "Albums" matches and its "Singles et EP" does not. It is nonetheless the honest
 * option available: the tiles carry no machine-readable type, only a subtitle whose type word is
 * localised the same way. So an unmatched shelf is not guessed at and not dropped — it keeps the
 * backend's own heading and falls through to [ArtistPage.otherShelves], which is precisely what
 * the screen did for every shelf before this merge existed.
 */
internal object ArtistPageMerger {

    private val SONG_TITLES = setOf("songs", "top songs", "popular", "popular releases")
    private val ALBUM_TITLES = setOf("albums", "discography")
    private val SINGLE_TITLES = setOf("singles", "singles and eps", "singles & eps", "eps")
    private val VIDEO_TITLES = setOf("videos", "music videos")

    fun merge(
        details: ArtistDetails?,
        libraryAlbums: List<UnifiedAlbum>,
        libraryTracks: List<UnifiedTrack>
    ): ArtistPage {
        val sections = details?.sections.orEmpty()
        val unplaced = mutableListOf<ArtistSection>()

        val shelfSongs = mutableListOf<UnifiedTrack>()
        val videos = mutableListOf<UnifiedTrack>()
        var albums: ArtistAlbumSection? = null
        var singles: ArtistAlbumSection? = null

        sections.forEach { section ->
            val key = section.title.normalised()
            when {
                section is ArtistTrackSection && key in SONG_TITLES -> shelfSongs += section.tracks
                section is ArtistTrackSection && key in VIDEO_TITLES -> videos += section.tracks
                section is ArtistAlbumSection && key in ALBUM_TITLES ->
                    albums = albums.mergeWith(section)
                section is ArtistAlbumSection && key in SINGLE_TITLES ->
                    singles = singles.mergeWith(section)
                else -> unplaced += section
            }
        }

        // The songs shelf first, then the library's own copies. TrackDeduplicator keeps the copy
        // from the lowest-priority source — your own files and your own server before anything
        // streamed — so ordering the inputs this way sets the *sequence* while leaving the choice
        // of which copy survives to the same rule the rest of the app uses.
        val topSongs = TrackDeduplicator.deduplicate(shelfSongs + libraryTracks)

        // Library records join the Albums bucket, since there is nothing on them saying whether
        // they are an album or a single. When the backend gave no albums shelf at all, they become
        // the bucket — this is the whole of an artist page for local files and Subsonic.
        val mergedAlbums = (albums?.albums.orEmpty() + libraryAlbums).distinctBy { it.id }
        val albumBucket = when {
            mergedAlbums.isEmpty() -> null
            else -> ArtistAlbumSection(
                title = albums?.title ?: "Albums",
                albums = mergedAlbums,
                moreBrowseId = albums?.moreBrowseId,
                moreParams = albums?.moreParams
            )
        }

        return ArtistPage(
            bio = details?.bio,
            imageUrl = details?.imageUrl,
            topSongs = topSongs,
            albums = albumBucket,
            singles = singles,
            videos = videos.distinctBy { it.id },
            related = details?.related.orEmpty(),
            otherShelves = unplaced
        )
    }

    /**
     * Two shelves in the same bucket — a page that lists "Albums" twice, which happens when a
     * backend splits by label. The first shelf's "more" endpoint wins: it is the one whose heading
     * the bucket is named after.
     */
    private fun ArtistAlbumSection?.mergeWith(other: ArtistAlbumSection): ArtistAlbumSection =
        if (this == null) {
            other
        } else {
            copy(albums = (albums + other.albums).distinctBy { it.id })
        }

    private fun String.normalised(): String = trim().lowercase()
}
