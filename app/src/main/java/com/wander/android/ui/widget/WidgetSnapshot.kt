package com.wander.android.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * What the Now Playing widget shows, as the service last saw it.
 *
 * Pushed into each widget's own Glance state by [PlaybackWidgetUpdater] rather than read from a
 * `MediaController` when the widget draws. Reading it there meant binding the playback service on
 * every redraw, and on a cold start the controller had not connected yet, so the widget drew
 * "Nothing playing" over a queue that was there. Glance state is kept on disk, so after the process
 * dies the widget still shows the last track, which a tap on play then resumes.
 */
internal data class WidgetSnapshot(
    val title: String?,
    val subtitle: String?,
    val artworkUrl: String?,
    val isPlaying: Boolean
) {
    companion object {
        private val TITLE = stringPreferencesKey("title")
        private val SUBTITLE = stringPreferencesKey("subtitle")
        private val ARTWORK = stringPreferencesKey("artwork")
        private val PLAYING = booleanPreferencesKey("playing")

        fun from(prefs: Preferences) = WidgetSnapshot(
            title = prefs[TITLE],
            subtitle = prefs[SUBTITLE],
            artworkUrl = prefs[ARTWORK],
            isPlaying = prefs[PLAYING] == true
        )

        /** Writes [snapshot] into every placed Now Playing widget, then redraws them. */
        suspend fun push(context: Context, snapshot: WidgetSnapshot) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java)
            if (ids.isEmpty()) return
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs.putOrRemove(TITLE, snapshot.title)
                    prefs.putOrRemove(SUBTITLE, snapshot.subtitle)
                    prefs.putOrRemove(ARTWORK, snapshot.artworkUrl)
                    prefs[PLAYING] = snapshot.isPlaying
                }
            }
            NowPlayingWidget().updateAll(context)
        }

        private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
            key: Preferences.Key<String>,
            value: String?
        ) {
            if (value == null) remove(key) else set(key, value)
        }
    }
}
