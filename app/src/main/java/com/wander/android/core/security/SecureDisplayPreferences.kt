package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages UI and theming preferences stored in encrypted preferences.
 */
internal class SecureDisplayPreferences(private val prefs: SharedPreferences) {

    private val _isAmoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val isAmoledBlack: StateFlow<Boolean> = _isAmoledBlack.asStateFlow()

    private val _isBackBlurEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACK_BLUR, true))
    val isBackBlurEnabled: StateFlow<Boolean> = _isBackBlurEnabled.asStateFlow()

    private val _isMonetDynamic = MutableStateFlow(prefs.getBoolean(KEY_MONET_DYNAMIC, true))
    val isMonetDynamic: StateFlow<Boolean> = _isMonetDynamic.asStateFlow()

    private val _isImmersivePlayer = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE_PLAYER, false))
    val isImmersivePlayer: StateFlow<Boolean> = _isImmersivePlayer.asStateFlow()

    private val _isCoverArtThemeEnabled = MutableStateFlow(prefs.getBoolean(KEY_COVER_ART_THEME, true))
    val isCoverArtThemeEnabled: StateFlow<Boolean> = _isCoverArtThemeEnabled.asStateFlow()

    private val _isReduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    val isReduceMotion: StateFlow<Boolean> = _isReduceMotion.asStateFlow()

    private val _isLetterByLetterLyricsEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_LETTER_BY_LETTER_LYRICS, true))
    val isLetterByLetterLyricsEnabled: StateFlow<Boolean> = _isLetterByLetterLyricsEnabled.asStateFlow()

    private val _isCoverCarouselEnabled = MutableStateFlow(prefs.getBoolean(KEY_COVER_CAROUSEL, true))
    val isCoverCarouselEnabled: StateFlow<Boolean> = _isCoverCarouselEnabled.asStateFlow()

    /** The Home shelf layout as JSON; null until the user customises it. Decoded by `ShelfConfigCodec`. */
    private val _homeLayout = MutableStateFlow(prefs.getString(KEY_HOME_LAYOUT, null))
    val homeLayout: StateFlow<String?> = _homeLayout.asStateFlow()

    /** The bottom bar's shortcuts as comma-separated names, in order; null until the user chooses. */
    private val _dockItems = MutableStateFlow(prefs.getString(KEY_DOCK_ITEMS, null))
    val dockItems: StateFlow<String?> = _dockItems.asStateFlow()

    /** The Library's visible sections as comma-separated names, in order; null until the user chooses. */
    private val _libraryTabs = MutableStateFlow(prefs.getString(KEY_LIBRARY_TABS, null))
    val libraryTabs: StateFlow<String?> = _libraryTabs.asStateFlow()

    fun setAmoledBlack(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_BLACK, enabled) }
        _isAmoledBlack.value = enabled
    }

    fun setBackBlurEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BACK_BLUR, enabled) }
        _isBackBlurEnabled.value = enabled
    }

    fun setMonetDynamic(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MONET_DYNAMIC, enabled) }
        _isMonetDynamic.value = enabled
    }

    fun setImmersivePlayer(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_IMMERSIVE_PLAYER, enabled) }
        _isImmersivePlayer.value = enabled
    }

    fun setCoverArtThemeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_COVER_ART_THEME, enabled) }
        _isCoverArtThemeEnabled.value = enabled
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REDUCE_MOTION, enabled) }
        _isReduceMotion.value = enabled
    }

    fun setLetterByLetterLyricsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LETTER_BY_LETTER_LYRICS, enabled) }
        _isLetterByLetterLyricsEnabled.value = enabled
    }

    fun setCoverCarouselEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_COVER_CAROUSEL, enabled) }
        _isCoverCarouselEnabled.value = enabled
    }

    fun setDockItems(names: String) {
        prefs.edit { putString(KEY_DOCK_ITEMS, names) }
        _dockItems.value = names
    }

    fun setLibraryTabs(names: String) {
        prefs.edit { putString(KEY_LIBRARY_TABS, names) }
        _libraryTabs.value = names
    }

    fun setHomeLayout(json: String) {
        prefs.edit { putString(KEY_HOME_LAYOUT, json) }
        _homeLayout.value = json
    }

    /**
     * Re-reads every value from storage. After a restore that is what the backup wrote; after the
     * store is cleared it is each default, so signing out and restoring share this one path.
     */
    fun reloadFlows() {
        _isAmoledBlack.value = prefs.getBoolean(KEY_AMOLED_BLACK, false)
        _isBackBlurEnabled.value = prefs.getBoolean(KEY_BACK_BLUR, true)
        _isMonetDynamic.value = prefs.getBoolean(KEY_MONET_DYNAMIC, true)
        _isImmersivePlayer.value = prefs.getBoolean(KEY_IMMERSIVE_PLAYER, false)
        _isCoverArtThemeEnabled.value = prefs.getBoolean(KEY_COVER_ART_THEME, true)
        _isReduceMotion.value = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        _isLetterByLetterLyricsEnabled.value = prefs.getBoolean(KEY_LETTER_BY_LETTER_LYRICS, true)
        _isCoverCarouselEnabled.value = prefs.getBoolean(KEY_COVER_CAROUSEL, true)
        _homeLayout.value = prefs.getString(KEY_HOME_LAYOUT, null)
        _dockItems.value = prefs.getString(KEY_DOCK_ITEMS, null)
        _libraryTabs.value = prefs.getString(KEY_LIBRARY_TABS, null)
    }
}

