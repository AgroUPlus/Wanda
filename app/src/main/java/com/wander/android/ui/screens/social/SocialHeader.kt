package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.theme.screenTitle

/**
 * The top of the Friends tab: the tab's name, and the two things you do *to* the roster.
 *
 * Finding people is the screen's primary action, so it is the filled one and the squarer one;
 * off-grid sharing is round and tonal beside it, so the pair no longer reads as two of the same.
 */
@Composable
internal fun SocialHeader(
    state: SocialUiState,
    contentPadding: PaddingValues,
    onOpenOffGrid: () -> Unit,
    onFindPeople: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding.headerInset())
            .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.nav_friends),
            style = MaterialTheme.typography.screenTitle,
            modifier = Modifier.weight(1f)
        )
        // Outside the `isPaired` gate, unlike everything beside it. Off-grid sharing is the one
        // thing on this tab that works with no server at all, so hiding it until an account exists
        // would hide it from exactly the person it was built for.
        Surface(
            onClick = onOpenOffGrid,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(HeaderButton)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Sensors, stringResource(R.string.social_share_off_grid))
            }
        }
        if (state.isPaired) {
            Surface(
                onClick = onFindPeople,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(HeaderButton)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PersonAdd, stringResource(R.string.common_find_people))
                }
            }
        }
    }
}

private val HeaderButton = 48.dp
