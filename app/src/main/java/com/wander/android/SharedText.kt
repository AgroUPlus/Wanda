package com.wander.android

/**
 * Picks the link out of text another app shared.
 *
 * Share sheets rarely send a bare URL: Spotify sends "Listen to X on Spotify: https://…", a browser
 * may send the page title on the line above. The first link wins, with the punctuation a sentence
 * leaves stuck to its end trimmed off — a link closing a sentence is not meant to end in a full
 * stop.
 */
internal object SharedText {

    private val LINK = Regex("""(?:https?|wanda)://\S+""", RegexOption.IGNORE_CASE)

    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '>', '"', '\'', '»', '”')

    /** The first `http(s)://` or `wanda://` link in [text], or null when it holds none. */
    fun firstLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return LINK.find(text)?.value?.trimEnd(*TRAILING)?.takeUnless { it.endsWith("://") }
    }
}
