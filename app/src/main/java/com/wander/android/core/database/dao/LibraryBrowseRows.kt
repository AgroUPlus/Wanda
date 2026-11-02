package com.wander.android.core.database.dao

/**
 * An artist in the user's library, grouped from the credits on their tracks and albums.
 *
 * [artistId] is one of the ids those rows carry, or null when none does (a local file, a YouTube
 * row). [trackCount] counts tracks only: an album known without its songs adds the artist, not a
 * song.
 */
data class LibraryArtistRow(
    val name: String,
    val artistId: String?,
    val trackCount: Int
)

/** A genre tag in the user's library and how many of its tracks carry it. */
data class LibraryGenreRow(
    val name: String,
    val trackCount: Int
)
