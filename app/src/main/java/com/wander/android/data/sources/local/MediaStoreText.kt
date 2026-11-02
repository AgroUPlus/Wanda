package com.wander.android.data.sources.local

import android.provider.MediaStore

/**
 * A MediaStore text column as the app should see it: null when the tag is really absent.
 *
 * MediaStore does not leave an untagged artist or album empty — it writes the literal
 * [MediaStore.UNKNOWN_STRING], `<unknown>`. Taken at face value that shows up in the UI as a
 * name, files every untagged song under one artist called `<unknown>`, and stops the app's own
 * "Unknown Artist" / "Unknown Album" handling from ever applying.
 */
internal fun String?.mediaStoreValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
