package com.wander.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.database.dao.DropDao
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.FriendDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.ShelfDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.RecordingSplitDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.ArtistEntity
import com.wander.android.core.database.entity.DropEntity
import com.wander.android.core.database.entity.FingerprintEntity
import com.wander.android.core.database.entity.FriendEntity
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.PlaylistEntity
import com.wander.android.core.database.entity.RecordingSplitEntity
import com.wander.android.core.database.entity.ShelfEntity
import com.wander.android.core.database.entity.TrackEntity

import androidx.room.TypeConverters
import com.wander.android.core.database.converter.SourceTypeConverter

/** Constructed by Hilt in [com.wander.android.di.AppModule]. */
@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        HistoryEntity::class,
        ShelfEntity::class,
        FriendEntity::class,
        DropEntity::class,
        FingerprintEntity::class,
        PlaylistEntity::class,
        RecordingSplitEntity::class
    ],
    version = 18,
    exportSchema = true
)
@TypeConverters(SourceTypeConverter::class)
abstract class WanderDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun historyDao(): HistoryDao
    abstract fun shelfDao(): ShelfDao
    abstract fun friendDao(): FriendDao
    abstract fun dropDao(): DropDao
    abstract fun fingerprintDao(): FingerprintDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recordingSplitDao(): RecordingSplitDao
}
