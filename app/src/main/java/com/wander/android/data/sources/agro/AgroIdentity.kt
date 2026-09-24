package com.wander.android.data.sources.agro

/** Who the stored device token resolves to, as the server sees it. */
internal data class AgroIdentity(val username: String, val role: String) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
}

/**
 * Drops a mutation's response body, keeping only whether it succeeded.
 *
 * These mutations return an acknowledgement the caller has no use for — what matters is that the
 * server accepted the write. Named rather than an empty `map { }` so that is legible as a choice.
 */
/** Shared with [AgroHandoffApi]: a mutation whose only interesting answer is whether it failed. */
internal fun <T> Result<T>.discardPayload(): Result<Unit> = map { Unit }
