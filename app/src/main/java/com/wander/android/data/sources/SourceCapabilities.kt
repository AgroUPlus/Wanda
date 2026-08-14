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
    val lyrics: Boolean = false
)
