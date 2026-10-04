package com.wander.android.data.sources.agro

/**
 * Holds back requests the server has already said it will refuse.
 *
 * A dead token used to stay in use: the presence heartbeat, every play or skip, and the library
 * sync — one upload per track — all kept presenting it, each answered 401. Servers commonly sit
 * behind CrowdSec, whose `http-generic-401-bf` bans an address after six refused POSTs in quick
 * succession, so a revoked device got its owner's whole network banned within seconds of opening
 * the app, and the owner could not reach the server to re-pair either.
 *
 * Keyed on the token itself, so pairing again — which always mints a new one — reopens it without
 * anyone having to remember to. Held in memory only: a fresh process tries once more, which is
 * also how a server restored from a backup that still knows the token is noticed.
 *
 * A process-wide object rather than an injected singleton because [AgroUploader] and
 * [AgroStreamFetcher] are also built by hand, and two gates would each let their own refusals
 * through.
 */
internal object AgroTokenGate {

    /** An inactive account can be approved or restored, so it is asked again — but rarely. */
    internal const val NOT_ACTIVE_RETRY_MS = 5 * 60_000L

    private class Refusal(val token: String, val error: AgroAuthError, var at: Long)

    @Volatile
    private var refusal: Refusal? = null

    /**
     * The error to fail with instead of sending, or null when the request may go.
     *
     * Letting an inactive account's retry through restamps the refusal first, so the requests that
     * pile up behind a five-minute wait go out as one probe, not all at once.
     */
    @Synchronized
    fun refusing(token: String, now: Long = System.currentTimeMillis()): AgroAuthError? {
        val held = refusal?.takeIf { it.token == token } ?: return null
        return when (held.error) {
            is AgroAuthError.NotActive -> if (now - held.at < NOT_ACTIVE_RETRY_MS) {
                held.error
            } else {
                held.at = now
                null
            }
            else -> held.error
        }
    }

    /** Remembers that [token] was refused, when [error] means the token itself is no good. */
    @Synchronized
    fun record(token: String, error: AgroAuthError, now: Long = System.currentTimeMillis()) {
        if (error is AgroAuthError.Rejected || error is AgroAuthError.NotActive) {
            refusal = Refusal(token, error, now)
        }
    }

    /**
     * [record] for the REST routes, from a bare status.
     *
     * Only 401 counts there: a 403 on those routes also means a feature the operator switched off,
     * or an upload or jam that is not this device's, and neither says anything about the token.
     */
    fun recordRestStatus(token: String, status: Int) {
        if (status == 401) record(token, AgroAuthError.Rejected("The server no longer accepts this device"))
    }

    /** The server accepted [token]; anything held against it is stale. */
    @Synchronized
    fun accepted(token: String) {
        if (refusal?.token == token) refusal = null
    }

    @Synchronized
    internal fun resetForTest() {
        refusal = null
    }
}
