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

    fun resetFlows() {
        _isAmoledBlack.value = false
        _isBackBlurEnabled.value = true
        _isMonetDynamic.value = true
        _isCoverArtThemeEnabled.value = true
        _isReduceMotion.value = false
        _isLetterByLetterLyricsEnabled.value = true
        _isCoverCarouselEnabled.value = true
        _isImmersivePlayer.value = false
    }
}

