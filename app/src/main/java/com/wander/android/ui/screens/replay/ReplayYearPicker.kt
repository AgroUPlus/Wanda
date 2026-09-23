package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Which year the story is about, and a way to switch it — so any year with plays can be opened,
 * including the one still in progress, without waiting for the season.
 *
 * Its own tap consumes the press, so opening it never counts as a tap on the story underneath.
 */
@Composable
internal fun ReplayYearPicker(
    year: Int,
    years: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (years.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.replay_choose_year)

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics { contentDescription = label }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(text = year.toString(), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    trailingIcon = if (option == year) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (option != year) onSelect(option)
                    }
                )
            }
        }
    }
}
