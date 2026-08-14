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
    val isLiked: Boolean = false
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
        fun fromUnifiedAlbum(album: UnifiedAlbum) = AlbumEntity(
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
            isLiked = album.isLiked
        )
    }
}
