package com.wander.android.di

import android.content.Context
import androidx.room.Room
import com.wander.android.core.audio.visualizer.AudioFftProcessor
import com.wander.android.core.database.WanderDatabase
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideTrackDao(db: WanderDatabase): TrackDao = db.trackDao()
    @Provides fun provideAlbumDao(db: WanderDatabase): AlbumDao = db.albumDao()
    @Provides fun provideHistoryDao(db: WanderDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideFftProcessor(): AudioFftProcessor = AudioFftProcessor()
}
