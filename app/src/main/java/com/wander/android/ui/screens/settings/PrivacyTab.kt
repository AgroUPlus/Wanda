package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard

/**
 * What this device records, and what other people are allowed to see.
 *
 * The visibility switches only appear with an Agro server paired: without one there is nobody they
 * could reveal anything to, and a switch that cannot do anything is worse than no switch.
 *
 * The visibility switches default off, on the server as well as here — a privacy setting that
 * defaults open has already leaked by the time the user finds it. Popular on Agro is managed in
 * Sync, defaulting on.
 */
internal fun LazyListScope.privacyTab(
    state: SettingsUiState,
    actions: SettingsActions
) {
    item(key = "incognito") {
        GroupedCard(
            items = listOf<@Composable () -> Unit>({
                SettingsToggle(
                    title = stringResource(R.string.settings_incognito),
                    subtitle = stringResource(R.string.settings_stop_recording_plays_stop_telling),
                    checked = state.incognito,
                    onCheckedChange = actions.onIncognitoChange,
                    icon = Icons.Rounded.LockPerson
                )
            })
        )
    }

    // Grouped rather than two single-toggle cards: both send a name to an external service
    // (LRCLIB, MusicBrainz) and nothing else in this list does, so they read as one decision —
    // "does this app talk to outside lookups at all" — with two independent answers, not two
    // unrelated settings that happen to sit next to each other.
    item(key = "sec_third_party_lookups") {
        SettingsSection(stringResource(R.string.settings_section_third_party_lookups))
    }
    item(key = "third_party_lookups") {
        GroupedCard(
            items = listOf<@Composable () -> Unit>(
                {
                    SettingsToggle(
                        title = stringResource(R.string.settings_online_lyrics_lookup),
                        subtitle = stringResource(R.string.settings_query_lrclib_when_no_local_or_server_lyrics),
                        checked = state.externalLyricsEnabled,
                        onCheckedChange = actions.onExternalLyricsChange,
                        icon = Icons.Rounded.Language
                    )
                },
                {
                    SettingsToggle(
                        title = stringResource(R.string.settings_musicbrainz_lookup),
                        subtitle = stringResource(R.string.settings_query_musicbrainz_for_accurate_artist_id),
                        checked = state.musicBrainzLookupEnabled,
                        onCheckedChange = actions.onMusicBrainzLookupChange,
                        icon = Icons.Rounded.Language
                    )
                },
                {
                    SettingsToggle(
                        title = stringResource(R.string.settings_podcast_search),
                        subtitle = stringResource(R.string.settings_podcast_search_subtitle),
                        checked = state.podcastIndexEnabled,
                        onCheckedChange = actions.onPodcastIndexChange,
                        icon = Icons.Rounded.Podcasts
                    )
                }
            )
        )
    }

    if (state.agroPaired && state.agroVisibility != null) {
        item(key = "visibility_header") {
            SettingsSection(stringResource(R.string.settings_section_visibility))
        }

        item(key = "visibility") {
            // Greyed out rather than hidden while state.incognito is on. The stored preferences
            // are left exactly as they were and come back untouched when it goes off —
            // state.incognito overrides them for as long as it is on, it does not rewrite them.
            // Hiding the rows instead would leave no way to tell what will be shared again after.
            val visibilityItems = buildList<@Composable () -> Unit> {
                if (state.incognito) {
                    add {
                        SettingsRow(
                            subtitle = stringResource(R.string.settings_incognito_so_none_being_shared),
                            title = stringResource(R.string.settings_paused_incognito)
                        )
                    }
                }
                add {
                    SettingsToggle(
                        title = stringResource(R.string.settings_show_what_i_m_playing),
                        subtitle = stringResource(R.string.settings_friends_see_current_track_can),
                        checked = state.agroVisibility.showNowPlaying && !state.incognito,
                        onCheckedChange = {
                            actions.onVisibilityChange(state.agroVisibility.copy(showNowPlaying = it))
                        },
                        enabled = !state.incognito,
                        icon = Icons.Rounded.Speaker
                    )
                }
                add {
                    SettingsToggle(
                        title = stringResource(R.string.settings_share_my_listening_stats),
                        subtitle = stringResource(R.string.settings_friends_see_top_artists_how),
                        checked = state.agroVisibility.showStats && !state.incognito,
                        onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(showStats = it)) },
                        enabled = !state.incognito,
                        icon = Icons.Rounded.QueryStats
                    )
                }
                add {
                    SettingsToggle(
                        title = stringResource(R.string.settings_let_people_find_me),
                        subtitle = stringResource(R.string.settings_username_appears_when_someone_searches),
                        checked = state.agroVisibility.discoverable && !state.incognito,
                        onCheckedChange = { actions.onVisibilityChange(state.agroVisibility.copy(discoverable = it)) },
                        enabled = !state.incognito,
                        icon = Icons.Rounded.Person
                    )
                }
            }
            GroupedCard(items = visibilityItems)
        }
    }

    if (state.agroPaired) {
        item(key = "proxy_relay") {
            GroupedCard(
                modifier = Modifier.padding(top = 16.dp),
                items = listOf<@Composable () -> Unit>({
                    SettingsToggle(
                        title = stringResource(R.string.settings_agro_privacy_relay),
                        subtitle = stringResource(R.string.settings_route_metadata_lyric_requests_through),
                        checked = state.agroProxyEnabled,
                        onCheckedChange = actions.onProxyChange,
                        icon = Icons.Rounded.Shield
                    )
                })
            )
        }
    }

    item(key = "forget") {
        GroupedCard(
            modifier = Modifier.padding(top = 16.dp),
            items = listOf<@Composable () -> Unit>({
                SettingsRow(
                    title = stringResource(R.string.settings_forget_all_credentials),
                    subtitle = stringResource(R.string.settings_signs_out_every_source_erases),
                    onClick = actions.onForgetEverything,
                    destructive = true,
                    icon = Icons.Rounded.DeleteForever
                )
            })
        )
    }
}
