package com.wander.android.core.security

import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device-local state must neither leave the device in a backup nor be overwritten by one. */
class SecureStorageBackupTest {

    private val deviceLocal: Map<String, Any> = mapOf(
        KEY_AGRO_DEVICE_ID to "this-phone",
        KEY_LOCAL_WATERMARK to 1_700_000_000L,
        KEY_PENDING_FORGET to setOf("track-1"),
        KEY_DUPLICATE_SCAN_CURSOR to "track-42",
        KEY_CATALOG_CURSOR to 900L,
        KEY_CATALOG_INDEX_VERSION to 3,
        KEY_CATALOG_PUBLISHED_AT to 1_600L,
        KEY_CATALOG_PUBLISHED_TRACK to "track-7",
        KEY_RELEASE_WATERMARK to 55L,
        KEY_RELEASE_PRIMED_ARTISTS to setOf("artist"),
        KEY_SEARCH_HISTORY to "radiohead\nbjork"
    )

    private fun prefsWith(values: Map<String, Any>) = FakeSharedPreferences().apply {
        edit {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
    }

    @Test
    fun everyListedKeyIsDeviceLocal() {
        assertEquals(deviceLocal.keys, DEVICE_LOCAL_KEYS)
    }

    @Test
    fun exportLeavesDeviceLocalKeysOut() {
        val backup = SecureStorageBackup(prefsWith(deviceLocal + (KEY_AMOLED_BLACK to true))) {}

        val exported = backup.exportAll()

        assertEquals(setOf(KEY_AMOLED_BLACK), exported.keys)
    }

    @Test
    fun importKeepsThisDevicesValuesAndAppliesTheRest() {
        val prefs = prefsWith(deviceLocal)
        val fromOtherPhone: Map<String, Any> = mapOf(
            KEY_AGRO_DEVICE_ID to "other-phone",
            KEY_LOCAL_WATERMARK to 1L,
            KEY_CATALOG_CURSOR to 1L,
            KEY_RELEASE_WATERMARK to 1L,
            KEY_AMOLED_BLACK to true
        )
        var reloaded = false

        SecureStorageBackup(prefs) { reloaded = true }.importAll(fromOtherPhone) { true }

        deviceLocal.forEach { (key, value) -> assertEquals(key, value, prefs.all[key]) }
        assertTrue(prefs.getBoolean(KEY_AMOLED_BLACK, false))
        assertTrue(reloaded)
    }

    @Test
    fun aRestoreThatReplacesEverythingDoesNotWipeDeviceLocalKeys() {
        val prefs = prefsWith(deviceLocal)

        SecureStorageBackup(prefs) {}.importAll(emptyMap()) { true }

        deviceLocal.keys.forEach { assertTrue(it, prefs.contains(it)) }
        assertFalse(prefs.contains(KEY_AMOLED_BLACK))
    }
}
