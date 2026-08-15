package com.wander.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity

/** Constructed by Hilt in [com.wander.android.di.AppModule]. */
@Database(
    entities = [TrackEntity::class, AlbumEntity::class, HistoryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class WanderDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun historyDao(): HistoryDao
}
