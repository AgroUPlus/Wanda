package com.wander.android.data.sources.archive

/**
 * Archive items ship the same recording in several formats. Preference order matches the Wander
 * TUI: lossless first, then the best lossy option.
 */
private val FORMAT_PREFERENCE = listOf("flac", "m4a", "ogg", "opus", "mp3")

private val AUDIO_EXTENSIONS = setOf("flac", "m4a", "ogg", "opus", "mp3", "wav", "aiff")

internal fun ArchiveFile.extension(): String = name.substringAfterLast('.', "").lowercase()

internal fun ArchiveFile.isAudio(): Boolean = extension() in AUDIO_EXTENSIONS

/** Lower is better; unknown formats sort last but are still playable. */
internal fun ArchiveFile.formatRank(): Int =
    FORMAT_PREFERENCE.indexOf(extension()).takeIf { it >= 0 } ?: FORMAT_PREFERENCE.size

/**
 * Groups an item's files by recording and picks the best format for each, so one item yields one
 * track per song rather than one track per file.
 */
internal fun List<ArchiveFile>.bestAudioPerRecording(): List<ArchiveFile> =
    filter { it.isAudio() }
        .groupBy { it.name.substringBeforeLast('.') }
        .values
        .mapNotNull { variants -> variants.minByOrNull { it.formatRank() } }
        .sortedWith(compareBy({ it.track?.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }))
