package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroDrop

/**
 * Where a conversation stopped being readable by the server.
 *
 * Notes were once sent in plain text and are now sealed to the recipient's key. The messages above
 * this line were written under the old rule and are still on the server exactly as they were sent —
 * encrypting them now is not possible, because the server would have to be handed the plaintext to
 * do it, which is the thing being avoided.
 *
 * So the line says what is true of each half rather than making a blanket claim. A padlock over a
 * whole conversation whose first half is in the clear would be the most misleading thing this
 * screen could draw.
 *
 * Shown only where there is a boundary to mark: a conversation that is encrypted throughout, or one
 * with no notes in it at all, gets nothing.
 */
@Composable
internal fun EncryptionBoundary(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Notes from here on are sealed on your device. The server stores " +
                        "them without being able to read them.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * The quiet padlock in the header, for a thread whose notes are all sealed.
 *
 * Deliberately understated and unlabelled: it is a statement of the ordinary case, not a badge to
 * be pleased about, and the screen-reader description carries the meaning that the icon alone
 * cannot. It is hidden rather than crossed out when a thread is *not* fully sealed, because the
 * boundary line in the conversation says that far more precisely than a header icon could.
 */
@Composable
internal fun EncryptedThreadLock(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(16.dp)
            .clearAndSetSemantics {
                contentDescription = "Notes in this conversation are end-to-end encrypted."
            }
    )
}

/**
 * The first message in [conversation] that was sealed, or null when there is no boundary to draw.
 *
 * Null in three distinct cases that all mean "no line here": nothing is encrypted, everything is,
 * or no message in the thread carries a note at all. Only messages *with* notes count — a drop with
 * no note has nothing to encrypt, so counting it would put the line in an arbitrary place.
 */
internal fun encryptionBoundaryId(conversation: List<AgroDrop>): String? {
    val withNotes = conversation.filter { !it.note.isNullOrBlank() || it.isEncrypted }
    val firstSealed = withNotes.indexOfFirst { it.isEncrypted }
    // No sealed note, or the thread has been sealed from its very first note: nothing to separate.
    if (firstSealed <= 0) return null
    return withNotes[firstSealed].id
}

/** True when every note in the thread is sealed, which is what the header padlock states. */
internal fun isFullySealed(conversation: List<AgroDrop>): Boolean {
    val withNotes = conversation.filter { !it.note.isNullOrBlank() || it.isEncrypted }
    return withNotes.isNotEmpty() && withNotes.all { it.isEncrypted }
}
