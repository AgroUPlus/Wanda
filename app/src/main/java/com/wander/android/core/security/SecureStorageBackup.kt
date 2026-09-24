package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Handles exporting and importing preferences for backup operations.
 */
internal class SecureStorageBackup(private val prefs: SharedPreferences) {

    fun exportAll(): Map<String, Any?> = prefs.all.filterKeys { it != KEY_AGRO_DEVICE_ID }

    fun isAccountKey(key: String): Boolean = key in ACCOUNT_KEYS

    fun importAll(values: Map<String, Any>, replaces: (String) -> Boolean) {
        val claimed = { key: String -> key != KEY_AGRO_DEVICE_ID && replaces(key) }
        prefs.edit {
            prefs.all.keys.filter(claimed).forEach { remove(it) }
            values.forEach { (key, value) ->
                if (!claimed(key)) return@forEach
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
    }
}
