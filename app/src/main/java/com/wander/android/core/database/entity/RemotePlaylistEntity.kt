package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist

/**
 * A backend's playlist as last listed, so the library shows it — and it opens — without the
 * network. Wanda's own playlists live in `local_playlists`; these are only ever a copy of what a
 * server said, replaced on every successful refresh of that source.
 */
@Entity(tableName = "remote_playlists", indices = [Index("source")])
data class RemotePlaylistEntity(
    @PrimaryKey val id: String,
    val source: SourceType,
    val name: String,
    val comment: String?,
    val coverArtUrl: String?,
    val songCount: Int,
    val durationMs: Long,
    val isPublic: Boolean,
    /** Whether this account may change it; see [UnifiedPlaylist.isEditable]. */
    val isEditable: Boolean,
    /** Where the backend listed it, so the order it chose survives a reload. */
    val position: Int,
    /** Track ids in order, comma-joined, once the playlist has been opened; null before then. */
    val trackIds: String?
) {
    fun toUnifiedPlaylist() = UnifiedPlaylist(
        id = id,
        source = source,
        name = name,
        comment = comment,
        coverArtUrl = coverArtUrl,
        songCount = songCount,
        durationMs = durationMs,
        isPublic = isPublic,
        isEditable = isEditable
    )

    fun storedTrackIds(): List<String>? = trackIds?.split(',')?.filter { it.isNotBlank() }

    companion object {
        fun from(playlist: UnifiedPlaylist, position: Int, trackIds: String?) = RemotePlaylistEntity(
            id = playlist.id,
            source = playlist.source,
            name = playlist.name,
            comment = playlist.comment,
            coverArtUrl = playlist.coverArtUrl,
            songCount = playlist.songCount,
            durationMs = playlist.durationMs,
            isPublic = playlist.isPublic,
            isEditable = playlist.isEditable,
            position = position,
            trackIds = trackIds
        )
    }
}
