package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Handles exporting and importing preferences for backup operations.
 *
 * [reload] re-reads every setting's flow once an import is written. Without it the app went on
 * showing what it loaded at startup, so a restore looked as though it had done nothing.
 */
internal class SecureStorageBackup(private val prefs: SharedPreferences, private val reload: () -> Unit) {

    fun exportAll(): Map<String, Any?> = prefs.all.filterKeys { it !in DEVICE_LOCAL_KEYS }

    fun isAccountKey(key: String): Boolean = key in ACCOUNT_KEYS

    fun importAll(values: Map<String, Any>, replaces: (String) -> Boolean) {
        // A device-local key in an older backup is ignored, and this device's own value is kept.
        val claimed = { key: String -> key !in DEVICE_LOCAL_KEYS && replaces(key) }
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
        reload()
    }
}

/**
 * Keys that describe this install rather than the user's choices, so they never leave it in a
 * backup and a restore never overwrites them.
 *
 * Most are cursors: positions in this device's own database, MediaStore or outbox, or on the Agro
 * server it was paired with. Restored onto a fresh install — or next to a different server — each
 * would claim work was done that never was here: a scan that skips older files, a catalogue read
 * from the middle, fingerprints never published, track removals sent for another phone.
 */
internal val DEVICE_LOCAL_KEYS: Set<String> = setOf(
    // This device's identity on the server; two installs sharing it would be one node.
    KEY_AGRO_DEVICE_ID,
    // MediaStore scan position: the newest file this device's scan has already read.
    KEY_LOCAL_WATERMARK,
    // Local track removals not yet reported to the server — an outbox for this library only.
    KEY_PENDING_FORGET,
    // A position in this device's own track table for the duplicate-linking pass.
    KEY_DUPLICATE_SCAN_CURSOR,
    // Catalogue read and publish positions on the paired server, and the marker saying which way
    // this device's copy was read. Unpairing drops them for the same reason.
    KEY_CATALOG_CURSOR,
    KEY_CATALOG_INDEX_VERSION,
    KEY_CATALOG_PUBLISHED_AT,
    KEY_CATALOG_PUBLISHED_TRACK,
    // The release-check position on the paired server and the artists it has primed: a pair, as
    // on unpair. The watermark without its primed set would announce a whole back catalogue.
    KEY_RELEASE_WATERMARK,
    KEY_RELEASE_PRIMED_ARTISTS,
    // What was typed into search on this phone. A log rather than a setting, and a backup file
    // is something people move around and share; nobody restores a device to get it back.
    KEY_SEARCH_HISTORY
)
