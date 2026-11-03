package com.wander.android.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.wander.android.MainActivity
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroDrop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Songs friends hand you, as conversations: one notification per friend, tagged with their name,
 * that grows a line for every drop until it is read, with a long-lived shortcut behind it so
 * Android files it under Conversations — where the user can make one friend a priority, bubble
 * them, or silence them without silencing everyone.
 *
 * Like the rest of [FriendNotifier]'s family, a drop reaches this only over the sync socket, which
 * is open only while Wanda is on screen. A drop sent while the app is closed is in the inbox at the
 * next launch rather than here: background delivery would need a push service the app does not
 * have, and this is built around not having one.
 *
 * The work runs in a scope of its own, not the caller's. The socket is collected with
 * `collectLatest`, so the next frame would cancel a notification still waiting on its cover.
 */
@Singleton
internal class DropNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val avatars: NotificationAvatars
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val style = FriendNotifier.style(context)

    /** One post at a time, so two drops landing together cannot each extend the old thread. */
    private val posting = Mutex()

    /** The friend whose thread is on screen; what they send is already in front of the user. */
    private val viewing = AtomicReference<String?>(null)

    /** MessagingStyle needs a "me". It shows only on the line a reaction from here adds. */
    private val self get() = Person.Builder().setName(context.getString(R.string.notif_self_name)).build()

    fun notify(drop: AgroDrop) {
        val from = drop.fromUser.trim()
        // A drop this account sent has no sender here; it is not news to the person who sent it.
        if (from.isEmpty()) return
        scope.launch { posting.withLock { post(drop, from) } }
    }

    private suspend fun post(drop: AgroDrop, from: String) {
        if (isViewing(from)) return
        val tag = SocialNotificationKeys.dropThreadTag(from)
        val face = avatars.face(from)
        val sender = Person.Builder()
            .setKey(SocialNotificationKeys.shortcutId(from))
            .setName(face.name)
            .setIcon(IconCompat.createWithBitmap(face.avatar))
            .build()
        val thread = style.shown(tag, SocialNotificationKeys.DROP_ID)
            ?.let(MessagingStyle::extractMessagingStyleFromNotification)
            ?: MessagingStyle(self)
        thread.setGroupConversation(false)
        val now = System.currentTimeMillis()
        drop.artworkUrl?.takeIf { it.isNotBlank() }?.let { avatars.load(it, COVER_PX) }?.let { cover ->
            // The cover rides as its own message: a message carrying a picture shows the picture
            // *instead of* its text, and the text — the song and the note — is the point.
            val track = DropNotificationText.trackLine(drop.trackTitle, drop.artistName)
            thread.addMessage(
                MessagingStyle.Message(track, now, sender)
                    .setData(NotificationPictureProvider.MIME_TYPE, NotificationPictureProvider.publish(context, cover))
            )
        }
        val text = DropNotificationText.message(drop.trackTitle, drop.artistName, drop.note) {
            context.getString(R.string.notif_drop_note, it)
        }
        thread.addMessage(text, now, sender)
        publishShortcut(from, sender)
        val notification = style.builder(context.getString(R.string.notif_drop_public))
            .setStyle(thread)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(SocialNotificationKeys.shortcutId(from))
            .setLocusId(LocusIdCompat(SocialNotificationKeys.shortcutId(from)))
            .setLargeIcon(face.avatar)
            .setWhen(now)
            // Each drop is a new song, so each one sounds; [FriendNotifier]'s repeats do not.
            .setOnlyAlertOnce(false)
            .setContentIntent(openThread(from))
            .addActions(from, drop.id, loved = false)
            .build()
        style.post(tag, SocialNotificationKeys.DROP_ID, notification)
    }

    /** Redraws [username]'s thread, silently, with a "❤️" from this account under the newest drop. */
    fun showReaction(username: String) = scope.launch {
        posting.withLock {
            val tag = SocialNotificationKeys.dropThreadTag(username)
            val shown = style.shown(tag, SocialNotificationKeys.DROP_ID) ?: return@withLock
            val thread = MessagingStyle.extractMessagingStyleFromNotification(shown) ?: return@withLock
            thread.addMessage(LOVE, System.currentTimeMillis(), self)
            val newest = shown.extras.getString(EXTRA_NEWEST_DROP) ?: return@withLock
            val redrawn = NotificationCompat.Builder(context, shown)
                .setStyle(thread)
                .setOnlyAlertOnce(true)
                .clearActions()
                .addActions(username, newest, loved = true)
                .build()
            style.post(tag, SocialNotificationKeys.DROP_ID, redrawn)
        }
    }

    fun dismiss(username: String) =
        style.cancel(SocialNotificationKeys.dropThreadTag(username), SocialNotificationKeys.DROP_ID)

    /** [username]'s thread came on screen: its notification is stale, and new drops are held back. */
    fun startViewing(username: String) {
        viewing.set(username.trim().lowercase())
        dismiss(username)
    }

    // Only if no other thread came on screen in between.
    fun stopViewing(username: String) {
        viewing.compareAndSet(username.trim().lowercase(), null)
    }

    private fun isViewing(username: String) = viewing.get() == username.trim().lowercase()

    /**
     * Without a long-lived shortcut Android 11+ files the notification outside Conversations. The
     * shortcut opens the same place the notification does: the inbox, inside this friend's thread.
     */
    private fun publishShortcut(username: String, person: Person) {
        val id = SocialNotificationKeys.shortcutId(username)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(person.name ?: username)
            .setLongLived(true)
            .setIsConversation()
            .setLocusId(LocusIdCompat(id))
            .setPerson(person)
            .setIcon(person.icon)
            .setIntent(threadIntent(username))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    /**
     * Takes down every thread and every conversation shortcut, for when the account is forgotten:
     * a shortcut is a friend's name and face, left in the launcher and the share sheet otherwise.
     */
    fun forgetAll() {
        val ids = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
            .map { it.id }
            .filter(SocialNotificationKeys::isConversationShortcut)
        ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        style.cancelAll()
    }

    /**
     * Play and Love it act on the newest drop, the one the thread ends on; Mark as read on all of
     * them. Once loved, Love it goes: pressing it again would only send the same heart.
     */
    private fun NotificationCompat.Builder.addActions(username: String, newestDrop: String, loved: Boolean) = apply {
        addExtras(android.os.Bundle().apply { putString(EXTRA_NEWEST_DROP, newestDrop) })
        addAction(action(R.string.notif_action_play, SocialActionReceiver.ACTION_PLAY, username, newestDrop).build())
        if (!loved) {
            addAction(
                action(R.string.notif_action_love, SocialActionReceiver.ACTION_LOVE, username, newestDrop)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
                    .build()
            )
        }
        addAction(
            action(R.string.notif_action_mark_read, SocialActionReceiver.ACTION_MARK_READ, username, null)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build()
        )
    }

    private fun action(label: Int, action: String, username: String, dropId: String?) =
        NotificationCompat.Action.Builder(
            R.drawable.ic_stat_sync,
            context.getString(label),
            SocialActionReceiver.pendingIntent(context, action, username, dropId)
        ).setShowsUserInterface(false)

    // The URI keeps each friend's PendingIntent apart, so one thread's tap never opens another's.
    private fun openThread(username: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        threadIntent(username),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun threadIntent(username: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(SocialNotificationKeys.inboxThreadLink(username).toUri())
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    companion object {
        /** What "Love it" sends back: the reaction the inbox's own heart sends. */
        const val LOVE = "❤️"

        /** Which drop Play and Love it point at, kept on the notification for its redraws. */
        private const val EXTRA_NEWEST_DROP = "com.wander.android.notification.NEWEST_DROP"

        /** Large enough for the shade's expanded picture, small enough to keep a dozen in memory. */
        private const val COVER_PX = 512
    }
}
