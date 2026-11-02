package com.wander.android.core.network

/**
 * Any absolute URL in a piece of text: a scheme, `://`, then everything up to whitespace.
 *
 * Ktor and OkHttp put the full request URL — host, path and query string — into their exception
 * messages ("Client request(GET https://…?token=…) invalid: 401"). A server URL here can be a
 * credential in its own right (Agro), and a query string can carry an API key or session token.
 */
private val URL_IN_TEXT = Regex("""\b[a-zA-Z][a-zA-Z0-9+.\-]*://\S+""")

/** [text] with every absolute URL in it replaced by a `<url>` placeholder. */
internal fun redactUrls(text: String): String = URL_IN_TEXT.replace(text, "<url>")

/**
 * What a log line may say about a failure that came out of an HTTP client: the exception's type
 * and its message with every URL removed, which keeps the useful part ("invalid: 401
 * Unauthorized", "timeout has expired") and drops the part that is a secret.
 *
 * Use this instead of `error.message`, and instead of passing the throwable to `Log.*`, whose
 * stack trace prints the same unredacted message.
 */
internal fun Throwable.loggableMessage(): String {
    val name = javaClass.simpleName
    val text = message?.let(::redactUrls) ?: return name
    return "$name: $text"
}
