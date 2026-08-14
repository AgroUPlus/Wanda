package com.wander.android.di

import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.archive.InternetArchiveSource
import com.wander.android.data.sources.local.LocalMusicSource
import com.wander.android.data.sources.navidrome.NavidromeSource
import com.wander.android.data.sources.ytmusic.YTMusicSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * The complete list of backends. Adding a source means adding one binding here — repositories
 * and the UI iterate the set and never name a concrete source.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {

    @Binds @IntoSet abstract fun bindNavidrome(source: NavidromeSource): IMusicSource

    @Binds @IntoSet abstract fun bindLocal(source: LocalMusicSource): IMusicSource

    @Binds @IntoSet abstract fun bindYtMusic(source: YTMusicSource): IMusicSource

    @Binds @IntoSet abstract fun bindArchive(source: InternetArchiveSource): IMusicSource
}
