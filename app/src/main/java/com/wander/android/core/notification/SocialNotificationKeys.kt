package com.wander.android.core.notification

/**
 * How the social notifications are told apart: a tag per person or blend, and an id per kind.
 *
 * Tags rather than `hashCode()`-derived ids, which is what these used to be. A hash can land on
 * another notification's id and silently replace it; a tag cannot, because the system keys a
 * notification on the pair. Usernames are compared case-insensitively everywhere else in the app
 * (the DAO queries use `COLLATE NOCASE`), so they are folded here too: "Marie" and "marie" are one
 * thread, not two.
 */
internal object SocialNotificationKeys {
    const val DROP_ID = 1
    const val REQUEST_ID = 2
    const val ACCEPT_ID = 3
    const val BLEND_ID = 4
    const val SUMMARY_ID = 49_000
    const val GROUP = "com.wander.android.SOCIAL"

    /** The one conversation notification for everything [username] sends. */
    fun dropThreadTag(username: String): String = "drop:${fold(username)}"

    fun requestTag(username: String): String = "request:${fold(username)}"

    fun acceptTag(username: String): String = "accept:${fold(username)}"

    fun blendTag(playlistId: String): String = "blend:$playlistId"

    /**
     * The conversation shortcut, and the LocusId that ties the notification to it. Named apart from
     * the static launcher shortcuts in `shortcuts.xml`, which a dynamic one with the same id would
     * otherwise collide with.
     */
    fun shortcutId(username: String): String = SHORTCUT_PREFIX + fold(username)

    /** Whether [id] is one of the [shortcutId]s, as opposed to any other shortcut the app has. */
    fun isConversationShortcut(id: String): Boolean = id.startsWith(SHORTCUT_PREFIX)

    /**
     * Keeps each action's PendingIntent apart. Two intents that differ only in their extras are the
     * same PendingIntent to the system, so without a distinct data URI one friend's "Play" would
     * be rewritten by the next friend's and play the wrong song.
     */
    fun actionData(action: String, key: String): String = "wanda-notification://$action/${fold(key)}"

    /** The in-app link a drop thread opens: the inbox, already inside that person's conversation. */
    fun inboxThreadLink(username: String): String = "wanda://inbox?$WITH=${encode(username)}"

    /** Where a friend request or an accept opens: that person's profile, which can answer one. */
    fun profileLink(username: String): String = "wanda://profile?$USER=${encode(username)}"

    /** The query parameters [inboxThreadLink] and [profileLink] carry the username in. */
    const val WITH = "with"
    const val USER = "user"

    private fun encode(username: String) = java.net.URLEncoder.encode(username.trim(), Charsets.UTF_8.name())

    private fun fold(username: String): String = username.trim().lowercase()

    private const val SHORTCUT_PREFIX = "conversation:"
}
