package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist

/**
 * Persisted local playlist holding track ids in order.
 */
@Entity(tableName = "local_playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String? = null,
    val coverArtUrl: String? = null,
    val trackIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toUnifiedPlaylist(): UnifiedPlaylist {
        val count = if (trackIds.isBlank()) 0 else trackIds.split(',').filter { it.isNotBlank() }.size
        return UnifiedPlaylist(
            id = id,
            source = SourceType.LOCAL,
            name = name,
            comment = comment,
            coverArtUrl = coverArtUrl,
            songCount = count
        )
    }
}
