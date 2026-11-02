package com.wander.android.core.playback.browse

import android.os.Bundle
import android.provider.MediaStore

/**
 * What "play … on Wanda" asked for, as the Assistant or a car hands it over.
 *
 * The request is a free-text query plus, when the Assistant understood more, a *focus* saying
 * whether the words named an album or an artist and the parsed names themselves. The focus is
 * worth honouring: "play Discovery" focused on an album means the album in order, not every
 * track whose title happens to contain the word.
 */
internal sealed interface VoiceQuery {

    /** "Play some music": no words at all. */
    data object Anything : VoiceQuery
    data class Album(val album: String) : VoiceQuery
    data class Artist(val artist: String) : VoiceQuery
    data class Text(val query: String) : VoiceQuery

    companion object {

        /** [query] with the focus and names from [extras], as `MEDIA_PLAY_FROM_SEARCH` carries them. */
        fun of(query: String?, extras: Bundle?): VoiceQuery = of(
            query = query,
            focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS),
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
        )

        fun of(query: String?, focus: String?, artist: String?, album: String?): VoiceQuery {
            val words = query?.trim().orEmpty()
            val albumName = album?.trim().orEmpty()
            val artistName = artist?.trim().orEmpty()
            return when {
                focus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE && albumName.isNotEmpty() -> Album(albumName)
                focus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE && artistName.isNotEmpty() -> Artist(artistName)
                words.isNotEmpty() -> Text(words)
                else -> Anything
            }
        }
    }
}
