package com.wander.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wander.android.data.model.SourceType

/**
 * A track its backend has confirmed as liked — read from its liked list, or acknowledged after a
 * like was sent. Only these are un-liked when the backend's list stops naming them: a like that
 * only ever existed in Wanda (a local file's rendition on Navidrome, say) is not the server's to
 * take back.
 */
@Entity(tableName = "remote_likes", indices = [Index("source")])
data class RemoteLikeEntity(
    @PrimaryKey val trackId: String,
    val source: SourceType
)
