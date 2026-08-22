package com.wander.android.data.model

/**
 * What a search is looking for.
 *
 * YouTube carries far more than songs, and until now the app asked it for songs and nothing else —
 * a podcast episode or a video upload simply could not be found. A source that only holds music
 * ignores this and answers the same way for every kind, which is why the default is [TRACKS]
 * rather than something each source has to opt into.
 *
 * Everything here still resolves to audio: an episode and a video both enter the one queue as a
 * [UnifiedTrack] and play through the same player. There is no video surface.
 */
enum class SearchKind(val label: String) {
    TRACKS("Music"),
    VIDEOS("Videos"),
    EPISODES("Podcasts")
}
