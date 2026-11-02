package com.wander.android.di

import android.content.Context
import com.wander.android.core.cache.AudioCacheManager
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.playback.PlayerFactory
import com.wander.android.core.playback.StreamResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideAudioCacheManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        identityKeyManager: IdentityKeyManager,
        secureStorage: SecureStorage
    ): AudioCacheManager = AudioCacheManager(
        context,
        okHttpClient,
        identityKeyManager,
        // Read once: the size is fixed for the life of the cache, so a new choice takes effect at
        // the next start — which the setting says.
        maxSizeBytes = secureStorage.downloads.streamCacheBytes.value
    )

    @Provides
    @Singleton
    fun providePlayerFactory(
        @ApplicationContext context: Context,
        cacheManager: AudioCacheManager,
        streamResolver: StreamResolver
    ): PlayerFactory = PlayerFactory(context, cacheManager, streamResolver)
}
