package com.wander.android.ui.screens.library.blend

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Blender
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape

/** The blends this account has been asked into, each with Join and Decline. Nothing when none. */
@Composable
internal fun BlendInvites(
    onJoined: (playlistId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlendInvitesViewModel = hiltViewModel()
) {
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val answering by viewModel.answering.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    if (invites.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.animateContentSize()) {
        invites.forEach { invite ->
            BlendInviteCard(
                invite = invite,
                working = answering == invite.playlistId,
                error = error?.takeIf { it.first == invite.playlistId }?.second,
                onJoin = { viewModel.join(invite.playlistId, onJoined) },
                onDecline = { viewModel.decline(invite.playlistId) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlendInviteCard(invite: BlendInfo, working: Boolean, error: String?, onJoin: () -> Unit, onDecline: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val others = invite.members.map { it.username }
    Surface(shape = RoundedCornerShape(28.dp), color = colors.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Faces(others.take(4))
                Spacer(Modifier.width(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(colors.tertiary, CircleShape)
                        .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    Icon(Icons.Rounded.Blender, null, tint = colors.onTertiary, modifier = Modifier.size(14.dp))
                    Text(
                        text = stringResource(R.string.blend_invite_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.onTertiary
                    )
                }
            }
            Text(
                text = stringResource(R.string.blend_invite_from, invite.createdBy, invite.title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onTertiaryContainer
            )
            Text(
                text = stringResource(R.string.blend_invite_with, others.joinToString { "@$it" }),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onTertiaryContainer
            )
            Text(
                text = error ?: stringResource(R.string.blend_invite_consent),
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, enabled = !working, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.blend_invite_decline))
                }
                Button(onClick = onJoin, enabled = !working, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    if (working) LoadingIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.blend_invite_join))
                }
            }
        }
    }
}

/** Everyone in it, overlapped like people standing together. */
@Composable
internal fun Faces(people: List<String>, size: Int = 32) {
    Box {
        people.forEachIndexed { index, name ->
            CuteAvatar(
                seed = name,
                size = size.dp,
                shape = PersonShape,
                showBorder = true,
                modifier = Modifier.offset(x = (index * size * 2 / 3).dp)
            )
        }
        Spacer(Modifier.width((size + (people.size - 1).coerceAtLeast(0) * size * 2 / 3).dp))
    }
}
