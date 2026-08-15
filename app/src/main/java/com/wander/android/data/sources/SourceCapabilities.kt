package com.wander.android.data.sources

/**
 * What a backend can actually do. The UI reads this to hide or disable actions instead of
 * calling a method that would silently return nothing.
 */
data class SourceCapabilities(
    val search: Boolean = false,
    val albums: Boolean = false,
    val playlists: Boolean = false,
    val likes: Boolean = false,
    val scrobble: Boolean = false,
    val radio: Boolean = false,
    val lyrics: Boolean = false,
    /**
     * The backend publishes its own recommendation feed — shelves it chose, not shelves derived
     * from local listening history. A source without this contributes nothing to Home's
     * recommendations rather than having some approximation invented for it.
     */
    val recommendations: Boolean = false,
    /**
     * The backend can mint a public link to a track. Only Navidrome can — it is the only source
     * that hosts the audio on a server the user controls — so the share action is absent
     * elsewhere rather than present and failing.
     */
    val share: Boolean = false
)
