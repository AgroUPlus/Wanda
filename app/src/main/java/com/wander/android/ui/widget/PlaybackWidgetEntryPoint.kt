package com.wander.android.ui.widget

import com.wander.android.core.playback.PlayerConnection
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * How the widget's `GlanceAppWidget`/`ActionCallback` classes — instantiated by the widget host's
 * own process, not by Hilt — reach [PlayerConnection].
 *
 * Neither `GlanceAppWidget` nor `ActionCallback` supports `@AndroidEntryPoint` the way an
 * `Activity` or `Service` does, so this is the documented way to pull a Hilt singleton out of the
 * `Application` context instead: an entry point interface, resolved with
 * [EntryPointAccessors.fromApplication].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlaybackWidgetEntryPoint {
    fun playerConnection(): PlayerConnection
}

internal fun playerConnectionFor(context: android.content.Context): PlayerConnection =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        PlaybackWidgetEntryPoint::class.java
    ).playerConnection()
