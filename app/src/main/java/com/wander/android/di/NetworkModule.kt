package com.wander.android.di

import com.wander.android.core.network.HttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** One connection pool for the whole app: API calls, artwork and audio all share it. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = HttpClientFactory.okHttpClient

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClientFactory.ktorClient
}
