package com.wander.android.ui.components

import androidx.compose.ui.graphics.Color

/**
 * The "listening now" green, matched to Spotify's device indicator so the state reads instantly.
 *
 * Deliberately a fixed colour rather than a theme role: it means one specific thing — another
 * device is playing right now — everywhere it appears, and it has to stay recognisable under a
 * wallpaper-derived palette that could otherwise tint it into the rest of the UI.
 */
internal val ListeningGreen = Color(0xFF1DB954)
