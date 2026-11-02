package com.wander.android.core.playback.browse

import androidx.media3.session.MediaSession

/**
 * Which apps may browse and search the library through the playback service.
 *
 * The service is exported so a car can bind to it, and an exported service can be bound by any
 * installed app. Transport controls stay open to every controller, as they always were — the
 * lockscreen, Bluetooth and the notification reach the session that way. Reading the library
 * is narrower: what someone listens to is theirs, and an arbitrary app should not be able to list
 * it just by asking.
 *
 * So browsing is answered for this app itself, for controllers Media3 counts as trusted (holding
 * `MEDIA_CONTENT_CONTROL`, or an enabled notification listener — System UI is one), and for the
 * car and Assistant hosts by package. The legacy browser protocol those hosts speak checks the
 * package a browser names against its calling uid on connect. A signature check per host, as
 * Google's UAMP sample does, would be stronger still; it needs the hosts' certificates kept here.
 */
internal class BrowserAccess(private val ownPackage: String) {

    fun mayBrowse(browser: MediaSession.ControllerInfo): Boolean =
        browser.packageName == ownPackage || browser.isTrusted || browser.packageName in CAR_AND_ASSISTANT

    private companion object {
        val CAR_AND_ASSISTANT = setOf(
            // Android Auto on the phone.
            "com.google.android.projection.gearhead",
            // Android Automotive OS: the media centre and its Assistant.
            "com.android.car.media",
            "com.google.android.carassistant",
            // The Assistant on the phone, for "play … on Wanda".
            "com.google.android.googlequicksearchbox"
        )
    }
}
