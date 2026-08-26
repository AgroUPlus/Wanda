package com.wander.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.DropDao
import com.wander.android.core.database.dao.FriendDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.ShelfDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.DropEntity
import com.wander.android.core.database.entity.FriendEntity
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.ShelfEntity
import com.wander.android.core.database.entity.TrackEntity

/** Constructed by Hilt in [com.wander.android.di.AppModule]. */
@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        HistoryEntity::class,
        ShelfEntity::class,
        FriendEntity::class,
        DropEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class WanderDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun historyDao(): HistoryDao
    abstract fun shelfDao(): ShelfDao
    abstract fun friendDao(): FriendDao
    abstract fun dropDao(): DropDao
}
