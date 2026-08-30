package com.wander.android.data.sources.agro

import java.io.IOException

/**
 * Why Agro would not accept us.
 *
 * The server already sends a usable sentence for each of these, and that sentence is kept as the
 * message. What it cannot carry is the *kind* of failure, and the kinds want different things from
 * the user: a wrong passphrase wants correcting, an account waiting on the admin wants patience, a
 * rate limit wants a pause, and an unreachable host wants a retry. Branching on prose would mean
 * matching on the server's wording, so the status code is turned into a type here instead.
 *
 * These extend [IOException] because every Agro call already fails with one, and nothing that
 * currently catches or reports `IOException` should have to learn about this hierarchy to keep
 * working.
 */
internal sealed class AgroAuthError(message: String, cause: Throwable? = null) :
    IOException(message, cause) {

    /** The credentials themselves were refused. Unknown account and wrong passphrase alike — the
     *  server deliberately does not tell the two apart, so neither can we. */
    internal class Rejected(message: String) : AgroAuthError(message)

    /** The account requires a second factor (2FA / TOTP). */
    internal class TwoFactorRequired(message: String) : AgroAuthError(message)

    /** The account exists but is `pending` (waiting for the admin) or `suspended`. Also what a
     *  revoked device token looks like once the account itself is gone. */
    internal class NotActive(message: String) : AgroAuthError(message)

    /** Too many attempts from this address. The server's window is five minutes. */
    internal class RateLimited(message: String) : AgroAuthError(message)

    /** Never got an answer: no network, bad host, TLS failure, timeout. */
    internal class Unreachable(message: String, cause: Throwable? = null) :
        AgroAuthError(message, cause)

    /** The server answered, but with something we cannot act on. */
    internal class Server(message: String) : AgroAuthError(message)

    internal companion object {
        /** How long [RateLimited] asks the caller to wait, matching the server's fixed window. */
        const val RATE_LIMIT_WINDOW_SECONDS = 300

        /**
         * Maps an HTTP status onto the reason for it.
         *
         * 401 is overloaded on the login endpoint: it answers "those credentials were not accepted",
         * "this account needs a code from its authenticator", and "this account is not active yet".
         */
        fun of(status: Int, serverMessage: String?): AgroAuthError {
            val message = serverMessage?.takeIf { it.isNotBlank() }
            return when {
                status == 429 -> RateLimited(
                    message ?: "Too many attempts. Wait a few minutes and try again."
                )
                status == 403 -> NotActive(message ?: "This account is not active")
                status == 401 && message?.contains("not active", ignoreCase = true) == true ->
                    NotActive(message)
                status == 401 && (message?.contains("authenticator", ignoreCase = true) == true || message?.contains("code", ignoreCase = true) == true) ->
                    TwoFactorRequired(message)
                status == 401 -> Rejected(message ?: "Those credentials were not accepted")
                else -> Server(message ?: "The server could not complete that (HTTP $status)")
            }
        }

        /**
         * Wraps whatever the HTTP client threw.
         *
         * An [AgroAuthError] is passed through: it already knows what it is, and re-wrapping it as
         * [Unreachable] would report a refused password as a network problem.
         */
        fun from(error: Throwable): AgroAuthError = when (error) {
            is AgroAuthError -> error
            else -> Unreachable(
                error.message?.takeIf { it.isNotBlank() } ?: "Could not reach the server",
                error
            )
        }
    }
}

/**
 * What to put in front of the user for each way authentication can fail.
 *
 * The server's own wording is good, but it cannot know what the app is able to offer next, and that
 * is the part that makes an error useful rather than merely accurate.
 */
internal fun AgroAuthError.explain(): String = when (this) {
    is AgroAuthError.TwoFactorRequired ->
        "2FA is enabled on this account. Pair with a Device Token or scan the QR Code from Devices & Sign-ins in the Agro dashboard."
    is AgroAuthError.Rejected ->
        "That username and passphrase were not accepted. If you generated a Device Token in the dashboard, paste it into the passphrase field."
    is AgroAuthError.NotActive ->
        "This account is not active yet. A new account waits for the server's admin to let it in; " +
            "once they have, tap Check again."
    is AgroAuthError.RateLimited ->
        "Too many attempts from this network. The server stops counting after about five minutes."
    is AgroAuthError.Unreachable ->
        "Could not reach that server. Check the address and that this device is online."
    is AgroAuthError.Server -> message ?: "The server could not complete that."
}
