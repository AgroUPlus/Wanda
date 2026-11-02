package com.wander.android.ui.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * The cover for the widget, as a small software bitmap.
 *
 * A widget is RemoteViews underneath: its bitmaps are parcelled to the launcher, and the platform
 * rejects an update whose bitmaps outgrow a budget tied to the screen size. [ARTWORK_PX] keeps a
 * cover far inside that. Hardware bitmaps cannot be parcelled at all, hence `allowHardware(false)`.
 *
 * Goes through the app's one [SingletonImageLoader], so the cover is usually already in its cache
 * from the player, and Navidrome artwork is fetched with the same signed client as everywhere else.
 * Null when there is no artwork or it could not be loaded; the widget then draws its placeholder.
 */
internal suspend fun loadWidgetArtwork(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(ARTWORK_PX)
        .allowHardware(false)
        .build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}

private const val ARTWORK_PX = 256
