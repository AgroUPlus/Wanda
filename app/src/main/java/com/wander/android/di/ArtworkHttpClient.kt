package com.wander.android.di

import javax.inject.Qualifier

/**
 * The shared base client plus Navidrome request signing, for loading artwork: Coil and the media
 * session's bitmap loader. Kept apart from the app's API client so artwork does not also land in
 * that client's HTTP cache, next to Coil's own disk cache.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArtworkHttpClient
