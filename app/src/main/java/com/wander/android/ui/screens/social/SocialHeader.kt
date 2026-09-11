package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.headerInset

/**
 * The top of the Friends tab: the tab's name, and the two things you do *to* the roster.
 *
 * It has been pared twice. It was three identical tonal circles — activity, find people, my profile
 * — which said nothing about which was which, and made the one that was *you* look like just
 * another action. Your face moved into the hero below, which is a better place to aim at and the
 * thing a hero is for; activity moved out entirely, because the tile under the hero already opens
 * it and says how much is unread, and a header button beside a tile for the same screen is the same
 * offer made twice.
 *
 * What is left is a title and the two actions that have nowhere else to be.
 */
@Composable
internal fun SocialHeader(
    state: SocialUiState,
    contentPadding: PaddingValues,
    onOpenOffGrid: () -> Unit,
    onFindPeople: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding.headerInset())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Friends",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Outside the `isPaired` gate, unlike everything beside it. Off-grid sharing is the one
            // thing on this tab that works with no server at all, so hiding it until an account
            // exists would hide it from exactly the person it was built for.
            FilledTonalIconButton(onClick = onOpenOffGrid) {
                Icon(Icons.Rounded.Sensors, contentDescription = "Share off-grid")
            }
            if (state.isPaired) {
                FilledTonalIconButton(onClick = onFindPeople) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Find people")
                }
            }
        }
    }
}
