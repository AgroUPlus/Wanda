package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shared-artwork keys taken on one destination. A key must be on screen once per destination:
 * an album listed twice on one page (under Albums and again under Appears on, say) gave two covers
 * answering to the same key, which the transition matched to either and flickered between.
 */
class SharedKeyClaims {
    private val owners = mutableMapOf<String, Any>()

    internal fun claim(key: String, owner: Any): Boolean {
        val current = owners[key]
        if (current != null && current !== owner) return false
        owners[key] = owner
        return true
    }

    internal fun release(key: String, owner: Any) {
        if (owners[key] === owner) owners.remove(key)
    }
}

/** One per destination, provided where each destination is composed. */
val LocalSharedKeyClaims = staticCompositionLocalOf<SharedKeyClaims?> { null }

/**
 * Whether this call site may fly [key]: the first on the destination to ask gets it, for as long as
 * it stays composed. With no registry around (a preview) every caller may.
 */
@Composable
internal fun rememberSharedKeyClaim(key: String?): Boolean {
    val claims = LocalSharedKeyClaims.current
    return when {
        key == null -> false
        claims == null -> true
        else -> remember(claims, key) { KeyClaim(claims, key) }.granted
    }
}

/** Taken as it is remembered, given back when it leaves composition (or never makes it in). */
private class KeyClaim(private val claims: SharedKeyClaims, private val key: String) : RememberObserver {
    val granted = claims.claim(key, this)

    override fun onRemembered() = Unit

    override fun onForgotten() = claims.release(key, this)

    override fun onAbandoned() = claims.release(key, this)
}
