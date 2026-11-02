package com.wander.android.core.network

import androidx.annotation.StringRes
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.navidrome.SubsonicServerException
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** What kind of failure the listener is told about. The exception's own text never reaches them. */
enum class ErrorKind { NETWORK, AUTH, SERVER, UNKNOWN }

/**
 * Sorts a failure into the four things a listener can act on: check the connection, sign in
 * again, wait for the server, or just try again.
 *
 * An exception's message is for logs (see [loggableMessage]): it is English, it can name hosts
 * and URLs, and it reads as a stack trace to anyone else.
 */
fun Throwable.errorKind(): ErrorKind = when (this) {
    is AgroAuthError.Unreachable -> ErrorKind.NETWORK
    is AgroAuthError.Rejected, is AgroAuthError.TwoFactorRequired, is AgroAuthError.NotActive -> ErrorKind.AUTH
    is AgroAuthError -> ErrorKind.SERVER
    is SubsonicServerException -> if (code in SUBSONIC_AUTH_CODES) ErrorKind.AUTH else ErrorKind.SERVER
    is ResponseException -> when (response.status.value) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> ErrorKind.AUTH
        else -> ErrorKind.SERVER
    }
    is UnknownHostException, is ConnectException, is NoRouteToHostException,
    is SSLException, is InterruptedIOException -> ErrorKind.NETWORK
    // Any other I/O failure got as far as an answer we could not use.
    is IOException -> ErrorKind.SERVER
    else -> ErrorKind.UNKNOWN
}

/** The sentence to show for [errorKind]. */
@StringRes
fun Throwable.userMessageRes(): Int = when (errorKind()) {
    ErrorKind.NETWORK -> R.string.error_network
    ErrorKind.AUTH -> R.string.error_auth
    ErrorKind.SERVER -> R.string.error_server
    ErrorKind.UNKNOWN -> R.string.error_unknown
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

/** Subsonic's "wrong credentials", "token auth unsupported" and "not authorised" codes. */
private val SUBSONIC_AUTH_CODES = setOf(40, 41, 42, 43, 44, 50)
