package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedArtist
import com.wander.android.data.model.UnifiedTrack

/**
 * What a search found, and which of the backends it asked did not answer.
 *
 * The search screen used to read a backend that failed as a backend with no matches: every error
 * became an empty list, so a server that was down, a timeout or a rejected sign-in all showed
 * "No matches" — a claim about the music that was really a claim about the network. Keeping the
 * failures next to the results lets the screen say which it is.
 */
data class SearchOutcome(
    val tracks: List<UnifiedTrack> = emptyList(),
    /** Library records matching the query, then those the backends returned for it. */
    val albums: List<UnifiedAlbum> = emptyList(),
    /** Only from backends that search artists — see `SourceCapabilities.collectionSearch`. */
    val artists: List<UnifiedArtist> = emptyList(),
    /** Every backend this search went out to, whether or not it answered. */
    val askedSources: List<SourceType> = emptyList(),
    /** The ones among [askedSources] whose search failed. */
    val failedSources: List<SourceType> = emptyList()
) {
    /**
     * Not one backend answered. Whatever results there are came from Room alone, and an empty list
     * says nothing about whether the music exists.
     */
    val allFailed: Boolean
        get() = askedSources.isNotEmpty() && failedSources.size == askedSources.size
}
