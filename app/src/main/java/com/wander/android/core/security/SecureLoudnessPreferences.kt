package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.wander.android.data.model.LoudnessMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loudness normalisation settings: which ReplayGain value to use, and a pre-amp in whole dB.
 *
 * On by default in [LoudnessMode.AUTO]: levelling only ever turns loud tracks down, so the worst a
 * listener notices is a mixed queue that no longer jumps in volume.
 */
internal class SecureLoudnessPreferences(private val prefs: SharedPreferences) {

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<LoudnessMode> = _mode.asStateFlow()

    private val _preampDb = MutableStateFlow(readPreamp())
    val preampDb: StateFlow<Int> = _preampDb.asStateFlow()

    fun setMode(mode: LoudnessMode) {
        prefs.edit { putString(KEY_LOUDNESS_MODE, mode.name) }
        _mode.value = mode
    }

    fun setPreampDb(db: Int) {
        val clamped = db.coerceIn(PREAMP_RANGE_DB)
        prefs.edit { putInt(KEY_LOUDNESS_PREAMP_DB, clamped) }
        _preampDb.value = clamped
    }

    fun reloadFlows() {
        _mode.value = readMode()
        _preampDb.value = readPreamp()
    }

    private fun readMode(): LoudnessMode =
        LoudnessMode.entries.firstOrNull { it.name == prefs.getString(KEY_LOUDNESS_MODE, null) } ?: LoudnessMode.AUTO

    private fun readPreamp(): Int = prefs.getInt(KEY_LOUDNESS_PREAMP_DB, 0).coerceIn(PREAMP_RANGE_DB)

    companion object {
        /** Only cuts: a boost would be capped away by the player's volume anyway. */
        val PREAMP_RANGE_DB = -12..0
    }
}
