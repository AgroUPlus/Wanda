package com.wander.android.core.notification

/**
 * The words a drop notification shows, kept apart from the notifier so they can be tested without a
 * device. The quotation marks around a note are the one localised part — French sets a note in
 * « guillemets » — so they come in as [quote] rather than being written here.
 */
internal object DropNotificationText {
    /** "Title — Artist", or the title alone when the sender's player knew no artist. */
    fun trackLine(title: String, artist: String): String {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        return if (cleanArtist.isEmpty()) cleanTitle else "$cleanTitle — $cleanArtist"
    }

    /**
     * One drop as a message line: the track, then the note under it when there is one. The note is
     * the reason they sent it, so it is never dropped for space — the shade truncates, not this.
     */
    fun message(title: String, artist: String, note: String?, quote: (String) -> String): String {
        val line = trackLine(title, artist)
        val cleanNote = note?.trim().orEmpty()
        return if (cleanNote.isEmpty()) line else "$line\n${quote(cleanNote)}"
    }
}
