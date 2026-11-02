package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.wander.android.R
import com.wander.android.ui.screens.settings.SettingsCategory.ABOUT
import com.wander.android.ui.screens.settings.SettingsCategory.APPEARANCE
import com.wander.android.ui.screens.settings.SettingsCategory.CONNECTIONS
import com.wander.android.ui.screens.settings.SettingsCategory.EXTERNAL
import com.wander.android.ui.screens.settings.SettingsCategory.PLAYBACK
import com.wander.android.ui.screens.settings.SettingsCategory.PRIVACY
import com.wander.android.ui.screens.settings.SettingsCategory.SYNC

/**
 * One searchable setting: the title its row shows, the page it is on, and the key of the list
 * item that holds it there, which the page scrolls to.
 *
 * [focusKey] is a `LazyColumn` item key from the page's own tab function, not a key of its own:
 * the two must be changed together, and a key that no longer matches only costs the scroll — the
 * page still opens.
 */
@Immutable
internal data class SettingsSearchEntry(
    @StringRes val label: Int,
    val category: SettingsCategory,
    val focusKey: String? = null
)

/**
 * Every row worth finding by name, as a static list.
 *
 * Kept by hand rather than collected from the pages, because a page is a `LazyListScope`
 * extension that only says what it holds by composing it — there is nothing to read without
 * drawing every page. A new setting row adds a line here.
 *
 * Rows that only appear in some states (the stream quality pickers while a source is signed in,
 * the Agro rows once paired) are listed all the same: the page they open explains why the row is
 * not there yet, which beats a search that finds nothing.
 */
internal val SettingsSearchIndex: List<SettingsSearchEntry> = listOf(
    // Connections
    SettingsSearchEntry(R.string.common_navidrome, CONNECTIONS, "sources"),
    SettingsSearchEntry(R.string.common_youtube_music, CONNECTIONS, "sources"),
    SettingsSearchEntry(R.string.deezer_account, CONNECTIONS, "sources"),
    SettingsSearchEntry(R.string.common_music_device, CONNECTIONS, "sources"),
    SettingsSearchEntry(R.string.settings_navidrome_quality_wifi, CONNECTIONS, "stream_quality"),
    SettingsSearchEntry(R.string.settings_navidrome_quality_mobile, CONNECTIONS, "stream_quality"),
    SettingsSearchEntry(R.string.settings_deezer_quality, CONNECTIONS, "stream_quality"),
    // Sync
    SettingsSearchEntry(R.string.settings_agro_device, SYNC, "agro"),
    SettingsSearchEntry(R.string.settings_sync_settings_agro, SYNC, "agro_toggles"),
    SettingsSearchEntry(R.string.settings_contribute_u201cpopular_agro_u201d, SYNC, "agro_toggles"),
    SettingsSearchEntry(R.string.settings_improve_agro, SYNC, "agro_toggles"),
    SettingsSearchEntry(R.string.settings_what_trade_has_done, SYNC, "agro_toggles"),
    SettingsSearchEntry(R.string.settings_p2p_device_sync, SYNC, "library_sync_toggles"),
    SettingsSearchEntry(R.string.settings_archive_server, SYNC, "library_sync_toggles"),
    SettingsSearchEntry(R.string.settings_free_up_space_device, SYNC, "library_sync_toggles"),
    SettingsSearchEntry(R.string.settings_sync_now, SYNC, "library_sync_now"),
    // Look & feel
    SettingsSearchEntry(R.string.settings_customize_home, APPEARANCE, "look_home"),
    SettingsSearchEntry(R.string.settings_dock_title, APPEARANCE, "look_navigation"),
    SettingsSearchEntry(R.string.settings_library_sections, APPEARANCE, "look_navigation"),
    SettingsSearchEntry(R.string.language_title, APPEARANCE, "look_language"),
    SettingsSearchEntry(R.string.settings_theme_mode, APPEARANCE, "look_colours"),
    SettingsSearchEntry(R.string.settings_match_system_colours, APPEARANCE, "look_colours"),
    SettingsSearchEntry(R.string.settings_dynamic_cover_art_theme, APPEARANCE, "look_colours"),
    SettingsSearchEntry(R.string.settings_true_black, APPEARANCE, "look_colours"),
    SettingsSearchEntry(R.string.settings_reduce_motion, APPEARANCE, "look_motion"),
    SettingsSearchEntry(R.string.settings_back_blur, APPEARANCE, "look_motion"),
    SettingsSearchEntry(R.string.settings_immersive_player, APPEARANCE, "look_now_playing"),
    SettingsSearchEntry(R.string.settings_letter_letter_lyrics, APPEARANCE, "look_now_playing"),
    // Playback & storage
    SettingsSearchEntry(R.string.settings_offline_mode, PLAYBACK, "playback"),
    SettingsSearchEntry(R.string.settings_ready_next_track, PLAYBACK, "playback"),
    SettingsSearchEntry(R.string.settings_skip_silence, PLAYBACK, "playback"),
    SettingsSearchEntry(R.string.settings_loudness_title, PLAYBACK, "loudness"),
    SettingsSearchEntry(R.string.settings_measure_library_now, PLAYBACK, "measuring"),
    SettingsSearchEntry(R.string.settings_measure_over_mobile_data, PLAYBACK, "measuring"),
    SettingsSearchEntry(R.string.settings_pause_measuring, PLAYBACK, "measuring"),
    SettingsSearchEntry(R.string.settings_what_has_been_measured, PLAYBACK, "measuring"),
    SettingsSearchEntry(R.string.settings_auto_download_liked, PLAYBACK, "downloads"),
    SettingsSearchEntry(R.string.settings_auto_download_predicted, PLAYBACK, "downloads"),
    SettingsSearchEntry(R.string.settings_download_wifi_only, PLAYBACK, "downloads"),
    SettingsSearchEntry(R.string.settings_download_limit, PLAYBACK, "downloads"),
    SettingsSearchEntry(R.string.settings_delete_all_downloads, PLAYBACK, "downloads"),
    SettingsSearchEntry(R.string.settings_download_liked_tracks_now, PLAYBACK, "storage"),
    SettingsSearchEntry(R.string.settings_pause_downloads, PLAYBACK, "storage"),
    SettingsSearchEntry(R.string.settings_clear_streaming_cache, PLAYBACK, "storage"),
    SettingsSearchEntry(R.string.settings_stream_cache_size, PLAYBACK, "storage"),
    // External
    SettingsSearchEntry(R.string.settings_custom_share_domain, EXTERNAL, "sharing"),
    SettingsSearchEntry(R.string.scrobbling_section, EXTERNAL, "scrobbling"),
    SettingsSearchEntry(R.string.settings_export_everything, EXTERNAL, "backup"),
    SettingsSearchEntry(R.string.settings_import_from_backup, EXTERNAL, "backup"),
    SettingsSearchEntry(R.string.cloud_backup_section, EXTERNAL, "cloud_backup"),
    SettingsSearchEntry(R.string.settings_new_music_from_artists_follow, EXTERNAL, "artist_release_notifications"),
    // Privacy
    SettingsSearchEntry(R.string.settings_incognito, PRIVACY, "incognito"),
    SettingsSearchEntry(R.string.settings_online_lyrics_lookup, PRIVACY, "third_party_lookups"),
    SettingsSearchEntry(R.string.settings_musicbrainz_lookup, PRIVACY, "third_party_lookups"),
    SettingsSearchEntry(R.string.settings_podcast_search, PRIVACY, "third_party_lookups"),
    SettingsSearchEntry(R.string.settings_show_what_i_m_playing, PRIVACY, "visibility"),
    SettingsSearchEntry(R.string.settings_share_my_listening_stats, PRIVACY, "visibility"),
    SettingsSearchEntry(R.string.settings_let_people_find_me, PRIVACY, "visibility"),
    SettingsSearchEntry(R.string.settings_agro_privacy_relay, PRIVACY, "proxy_relay"),
    SettingsSearchEntry(R.string.settings_forget_all_credentials, PRIVACY, "forget"),
    // About
    SettingsSearchEntry(R.string.replay_title, ABOUT, "about"),
    SettingsSearchEntry(R.string.common_duplicate_recordings, ABOUT, "about"),
    SettingsSearchEntry(R.string.settings_version, ABOUT, "about"),
    SettingsSearchEntry(R.string.settings_agrouplus, ABOUT, "credits"),
    SettingsSearchEntry(R.string.settings_check_updates_launch, ABOUT, "credits"),
    SettingsSearchEntry(R.string.settings_debug_mode_title, ABOUT, "credits")
)

/**
 * The entries whose label, or whose page's name, contains every word of [query], ignoring case
 * and accents. [resolve] turns a string resource into the reader's language; it is a parameter so
 * the matching is testable without Android.
 */
internal fun searchSettings(
    query: String,
    resolve: (Int) -> String,
    index: List<SettingsSearchEntry> = SettingsSearchIndex
): List<SettingsSearchEntry> {
    val words = normalise(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return index.filter { entry ->
        val haystack = normalise(resolve(entry.label) + " " + resolve(entry.category.label))
        words.all { it in haystack }
    }
}

private val Diacritics = Regex("\\p{Mn}+")

private fun normalise(text: String): String =
    Diacritics.replace(java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD), "")
        .lowercase()
        .trim()
