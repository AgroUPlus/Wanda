package com.wander.android.core.backup

import kotlinx.serialization.Serializable

/**
 * Everything this device remembers about itself, in a form that survives a reinstall.
 *
 * The contents are whatever [com.wander.android.core.security.SecureStorage] holds, key for key,
 * rather than a hand-written list of settings. That is deliberate: an enumerated list is a second
 * place to remember every preference, and the one that gets forgotten — a setting added next month
 * would silently stop being backed up, and nothing would fail. Iterating the store cannot drift
 * from it.
 *
 * The consequence is that a backup contains credentials, because that store is also the only place
 * credentials live. So it is never written in the clear; see [BackupCodec].
 *
 * Versioned by [BackupCodec.FORMAT] as a whole, not here: a field added later is one a newer build
 * reads and an older one ignores, and a change that cannot be read that way is a new format.
 */
@Serializable
internal data class BackupDocument(
    /** Preferences, without the sign-ins — those travel in [accounts] so either can be left out. */
    val entries: Map<String, BackupEntry> = emptyMap(),
    val accounts: Map<String, BackupEntry> = emptyMap(),
    /** Every play this device remembers, if the user asked for them. */
    val history: List<BackupPlay> = emptyList(),
    /** Saved Agro Replay recaps, which may outlive the plays in [history]. */
    val recaps: List<BackupRecap> = emptyList(),
    val tracks: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val splits: List<BackupRecordingPair> = emptyList(),
    val links: List<BackupRecordingPair> = emptyList(),
    val episodes: List<BackupEpisode> = emptyList(),
    /** Subscribed podcast feeds; travel with the LIBRARY section. */
    val podcasts: List<BackupPodcast> = emptyList(),
    /**
     * One digest per section the backup carries, keyed by [BackupSection.name] — which sections
     * were chosen, and proof each arrived whole; see [verifyIntegrity].
     */
    val manifest: Map<String, SectionDigest> = emptyMap()
)

/**
 * One stored preference, with its type written down.
 *
 * `SharedPreferences` is typed — `getBoolean` on a key written as a string throws — so a backup
 * that flattened everything to text would restore a file that crashes the next time a setting is
 * read. The tag is what makes the round trip exact.
 */
@Serializable
internal data class BackupEntry(
    val type: String,
    val value: String? = null,
    val values: List<String>? = null
)

/** The preference map as it comes out of the store, tagged for transport. */
internal fun Map<String, Any?>.toBackupEntries(): Map<String, BackupEntry> =
    mapNotNull { (key, value) ->
        val entry = when (value) {
            is Boolean -> BackupEntry(TYPE_BOOL, value.toString())
            is Int -> BackupEntry(TYPE_INT, value.toString())
            is Long -> BackupEntry(TYPE_LONG, value.toString())
            is Float -> BackupEntry(TYPE_FLOAT, value.toString())
            is String -> BackupEntry(TYPE_STRING, value)
            // `getStringSet` is the one collection `SharedPreferences` stores, and the only one
            // that can appear here.
            is Set<*> -> BackupEntry(TYPE_SET, values = value.filterIsInstance<String>())
            // A type this code has never seen is skipped rather than guessed at: restoring it as
            // the wrong type would break the setting it belongs to on every later read.
            else -> null
        }
        entry?.let { key to it }
    }.toMap()

/**
 * Back to the types the preference store expects.
 *
 * An entry whose payload does not parse is dropped, not defaulted. A malformed number means the
 * file was damaged or edited, and writing a zero there would look like a deliberate setting.
 */
internal fun Map<String, BackupEntry>.toPreferenceValues(): Map<String, Any> =
    mapNotNull { (key, entry) ->
        val value: Any? = when (entry.type) {
            TYPE_BOOL -> entry.value?.toBooleanStrictOrNull()
            TYPE_INT -> entry.value?.toIntOrNull()
            TYPE_LONG -> entry.value?.toLongOrNull()
            TYPE_FLOAT -> entry.value?.toFloatOrNull()
            TYPE_STRING -> entry.value
            TYPE_SET -> entry.values?.toSet()
            else -> null
        }
        value?.let { key to it }
    }.toMap()

private const val TYPE_BOOL = "bool"
private const val TYPE_INT = "int"
private const val TYPE_LONG = "long"
private const val TYPE_FLOAT = "float"
private const val TYPE_STRING = "string"
private const val TYPE_SET = "set"
