package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["source", "sourceAlbumId"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"])
    ]
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val sourceAlbumId: String,
    val source: SourceType,
    val title: String,
    val artist: String,
    val artistId: String?,
    val coverArtUrl: String?,
    val songCount: Int = 0,
    val durationMs: Long = 0L,
    val year: Int? = null,
    val genre: String? = null,
    val isLiked: Boolean = false,
    /**
     * Whether this record is part of the user's collection, as opposed to one merely seen.
     *
     * The albums table is both a library and a cache. Every tile on an artist's page is written
     * here so the album screen has a header before its tracks land — which meant browsing an
     * artist filed their entire discography into your Library tab. Browsing is not owning, and
     * this is the flag that says which is which. Only the library-browse path sets it.
     */
    val isLibrary: Boolean = false
) {
    fun toUnifiedAlbum() = UnifiedAlbum(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artistId = artistId,
        coverArtUrl = coverArtUrl,
        songCount = songCount,
        durationMs = durationMs,
        year = year,
        genre = genre,
        isLiked = isLiked
    )

    companion object {
        /**
         * [isLibrary] is a property of how the album was *found*, not of the album, so it is a
         * parameter here rather than a field on `UnifiedAlbum` — the same record is library when
         * your server lists it and not library when it turns up on an artist's page.
         */
        fun fromUnifiedAlbum(album: UnifiedAlbum, isLibrary: Boolean = false) = AlbumEntity(
            id = album.id,
            sourceAlbumId = album.id.substringAfter(':', album.id),
            source = album.source,
            title = album.title,
            artist = album.artist,
            artistId = album.artistId,
            coverArtUrl = album.coverArtUrl,
            songCount = album.songCount,
            durationMs = album.durationMs,
            year = album.year,
            genre = album.genre,
            isLiked = album.isLiked,
            isLibrary = isLibrary
        )
    }
}
