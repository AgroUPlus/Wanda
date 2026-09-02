package com.wander.android.di

import android.content.Context
import androidx.room.Room
import com.wander.android.core.database.WANDER_MIGRATIONS
import com.wander.android.core.database.WanderDatabase
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.DropDao
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.FriendDao
import com.wander.android.core.database.dao.ShelfDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage =
        SecureStorage.create(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WanderDatabase =
        Room.databaseBuilder(context, WanderDatabase::class.java, "wanda_music.db")
            // Real migrations, deliberately with no destructive fallback. The tracks table now
            // carries library-sync state — a content hash that costs minutes of hashing to
            // rebuild, and the record of which files the server has confirmed — so dropping it on
            // a version bump would silently re-upload everything. A missing migration is now a
            // crash on the next build, which is the right time to find out.
            .addMigrations(*WANDER_MIGRATIONS)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    runCatching {
                        db.execSQL("DELETE FROM tracks WHERE source = 'INTERNET_ARCHIVE'")
                        db.execSQL("DELETE FROM albums WHERE source = 'INTERNET_ARCHIVE'")
                    }
                }
            })
            .build()

    @Provides fun provideTrackDao(db: WanderDatabase): TrackDao = db.trackDao()
    @Provides fun provideAlbumDao(db: WanderDatabase): AlbumDao = db.albumDao()
    @Provides fun provideArtistDao(db: WanderDatabase): ArtistDao = db.artistDao()
    @Provides fun provideHistoryDao(db: WanderDatabase): HistoryDao = db.historyDao()
    @Provides fun provideShelfDao(db: WanderDatabase): ShelfDao = db.shelfDao()
    @Provides fun provideFriendDao(db: WanderDatabase): FriendDao = db.friendDao()
    @Provides fun provideDropDao(db: WanderDatabase): DropDao = db.dropDao()
    @Provides fun provideFingerprintDao(db: WanderDatabase): FingerprintDao = db.fingerprintDao()
    @Provides fun providePlaylistDao(db: WanderDatabase): com.wander.android.core.database.dao.PlaylistDao = db.playlistDao()

    @Provides fun provideRecordingSplitDao(db: WanderDatabase): com.wander.android.core.database.dao.RecordingSplitDao = db.recordingSplitDao()
    @Provides fun provideRecordingLinkDao(db: WanderDatabase): com.wander.android.core.database.dao.RecordingLinkDao = db.recordingLinkDao()
    @Provides fun provideCanonicalMetadataDao(db: WanderDatabase): com.wander.android.core.database.dao.CanonicalMetadataDao = db.canonicalMetadataDao()
    @Provides fun provideTrackFeatureDao(db: WanderDatabase): com.wander.android.core.database.dao.TrackFeatureDao = db.trackFeatureDao()
    @Provides fun provideMelodyContourDao(db: WanderDatabase): com.wander.android.core.database.dao.MelodyContourDao = db.melodyContourDao()

    @Provides fun provideRecordingFingerprintDao(db: WanderDatabase): com.wander.android.core.database.dao.RecordingFingerprintDao = db.recordingFingerprintDao()
}
