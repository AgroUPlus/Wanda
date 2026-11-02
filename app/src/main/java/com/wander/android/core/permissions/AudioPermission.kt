package com.wander.android.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** `READ_MEDIA_AUDIO` on API 33+, `READ_EXTERNAL_STORAGE` below it. */
val AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** Only meaningful from API 33; below that notifications need no grant. */
val NOTIFICATION_PERMISSION: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS
    else null

/**
 * Reaching another device on the same network, from Android 16 onwards.
 *
 * Below API 36 local traffic is covered by `INTERNET` and there is nothing to ask for, so this is
 * null and [hasLocalNetworkPermission] answers true.
 */
val LOCAL_NETWORK_PERMISSION: String? =
    if (Build.VERSION.SDK_INT >= 36) "android.permission.ACCESS_LOCAL_NETWORK" else null

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasAudioPermission(): Boolean = hasPermission(AUDIO_PERMISSION)

/**
 * Opens this app's page in system settings, where a permission Android no longer asks about can
 * still be granted. A new task when started from something other than an activity, as Android
 * requires.
 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/** Whether this device may open connections to peers on the same network. */
fun Context.hasLocalNetworkPermission(): Boolean =
    LOCAL_NETWORK_PERMISSION?.let { hasPermission(it) } ?: true
