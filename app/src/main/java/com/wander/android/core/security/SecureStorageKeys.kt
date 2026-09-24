package com.wander.android.core.security

internal const val PREFS_NAME = "wanda_secure_vault"

internal const val KEY_NAVIDROME_URL = "key_navidrome_url"
internal const val KEY_NAVIDROME_USER = "key_navidrome_user"
internal const val KEY_NAVIDROME_TOKEN = "key_navidrome_token"

internal const val KEY_YTM_COOKIE = "key_ytm_cookie"
internal const val KEY_YTM_VISITOR = "key_ytm_visitor"
internal const val KEY_YTM_ACCOUNT = "key_ytm_account"

internal const val KEY_AGRO_URL = "key_agro_url"
internal const val KEY_AGRO_USER = "key_agro_user"
internal const val KEY_AGRO_KEY = "key_agro_key"
internal const val KEY_AGRO_PETNAME = "key_agro_petname"
internal const val KEY_AGRO_VAULT_KEY = "key_agro_vault_key"
internal const val KEY_AGRO_IDENTITY_PRIV = "key_agro_identity_priv"
internal const val KEY_AGRO_IDENTITY_PUB = "key_agro_identity_pub"
internal const val KEY_AGRO_SYNC_SETTINGS = "key_agro_sync_settings"
internal const val KEY_AGRO_DEVICE_ID = "key_agro_device_id"

internal val ACCOUNT_KEYS = setOf(
    KEY_NAVIDROME_URL, KEY_NAVIDROME_USER, KEY_NAVIDROME_TOKEN, KEY_YTM_COOKIE,
    KEY_AGRO_URL, KEY_AGRO_USER, KEY_AGRO_KEY, KEY_AGRO_PETNAME, KEY_AGRO_VAULT_KEY,
    KEY_AGRO_IDENTITY_PRIV, KEY_AGRO_IDENTITY_PUB
)

internal const val KEY_AGRO_CATALOG_TRADE = "key_agro_catalog_trade"
const val KEY_CATALOG_CURSOR = "catalog_cursor"
const val KEY_CATALOG_PUBLISHED_AT = "catalog_published_at"
internal const val KEY_AGRO_CAPABILITIES = "key_agro_capabilities"
internal const val KEY_AGRO_P2P_SYNC = "key_agro_p2p_sync"
internal const val KEY_AGRO_SERVER_ARCHIVE = "key_agro_server_archive"
internal const val KEY_AGRO_POPULARITY = "key_agro_popularity_contribution"
internal const val KEY_AGRO_LIBRARY_SYNC = "key_agro_library_sync"
internal const val KEY_AGRO_PROXY_ENABLED = "key_agro_proxy_enabled"

internal const val KEY_EXTERNAL_LYRICS = "key_external_lyrics"
internal const val KEY_OFFLINE_MODE = "key_offline_mode"
internal const val KEY_PRELOAD_NEXT = "key_preload_next"
internal const val KEY_SKIP_SILENCE = "key_skip_silence"
internal const val KEY_INDEX_ON_MOBILE_DATA = "key_index_on_mobile_data"
internal const val KEY_RADIO_MODE = "key_radio_mode"

internal const val KEY_AMOLED_BLACK = "key_amoled_black"
internal const val KEY_BACK_BLUR = "key_back_blur"
internal const val KEY_MONET_DYNAMIC = "key_monet_dynamic"
internal const val KEY_REDUCE_MOTION = "key_reduce_motion"
internal const val KEY_LETTER_BY_LETTER_LYRICS = "key_letter_by_letter_lyrics"
internal const val KEY_IMMERSIVE_PLAYER = "key_immersive_player"
internal const val KEY_COVER_ART_THEME = "key_cover_art_theme"
internal const val KEY_COVER_CAROUSEL = "key_cover_carousel"

internal const val KEY_AUTO_UPDATE_CHECK = "key_auto_update_check"
internal const val KEY_RELEASE_NOTIFICATIONS = "key_release_notifications"
internal const val KEY_RELEASE_WATERMARK = "key_release_watermark"
internal const val KEY_LAST_NOTIFIED_RELEASE = "key_last_notified_release"
internal const val KEY_REPLAY_SEEN_YEAR = "key_replay_seen_year"
internal const val KEY_DUPLICATE_SCAN_CURSOR = "duplicate_scan_cursor"
internal const val KEY_INCOGNITO = "key_incognito"
internal const val KEY_PENDING_FORGET = "key_pending_forget"
internal const val KEY_LOCAL_WATERMARK = "key_local_scan_watermark"
internal const val KEY_LOCAL_FOLDER = "key_local_scan_folder"
internal const val KEY_LOCAL_FOLDER_LABEL = "key_local_scan_folder_label"
internal const val KEY_SETUP_DONE = "key_setup_complete"
internal const val KEY_SHARE_DOMAIN = "key_share_domain"
internal const val KEY_AGRO_SHARE_DOMAIN = "key_agro_share_domain"
internal const val KEY_AGRO_SHARE_HOSTS = "key_agro_share_hosts"
internal const val KEY_PREFERRED_AUDIO_LANGUAGE = "key_preferred_audio_language"

internal val HOST_REGEX = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""")
