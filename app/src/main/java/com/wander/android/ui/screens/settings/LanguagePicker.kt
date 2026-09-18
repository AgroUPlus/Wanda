package com.wander.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.i18n.AppLocale
import com.wander.android.core.i18n.supportedAppLocales

/**
 * The language row, and the dialog behind it.
 *
 * Renders nothing at all when the build ships a single language: a picker with one entry is a
 * control that cannot do anything, and offering it would suggest translations exist that do not.
 * See [supportedAppLocales].
 */
@Composable
internal fun LanguageSetting(
    currentTag: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val context = LocalContext.current
    val locales = remember(context) { supportedAppLocales(context) }
    if (locales.isEmpty()) return

    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selected = locales.firstOrNull { it.tag.equals(currentTag, ignoreCase = true) }
        ?: locales.first()

    SettingsRow(
        title = stringResource(R.string.language_title),
        subtitle = selected.displayName(),
        onClick = { showDialog = true },
        icon = icon,
        modifier = modifier
    )

    if (showDialog) {
        LanguageDialog(
            locales = locales,
            selected = selected,
            onPick = {
                showDialog = false
                if (!it.tag.equals(currentTag, ignoreCase = true)) onLanguageChange(it.tag)
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun LanguageDialog(
    locales: List<AppLocale>,
    selected: AppLocale,
    onPick: (AppLocale) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        text = {
            // A plain scrolling Column rather than a LazyColumn: the list is as long as the number
            // of languages translated, which is a handful, and a lazy list inside a dialog has to
            // be given a height it cannot work out for itself.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                locales.forEach { locale ->
                    LanguageOption(
                        locale = locale,
                        selected = locale.tag == selected.tag,
                        onClick = { onPick(locale) }
                    )
                }
            }
        }
    )
}

@Composable
private fun LanguageOption(locale: AppLocale, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(text = locale.flag, style = MaterialTheme.typography.titleLarge)
        Text(
            text = locale.displayName(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** The system entry has no endonym of its own — it is whatever the phone is set to. */
@Composable
private fun AppLocale.displayName(): String =
    if (isSystemDefault) stringResource(R.string.language_system_default) else endonym
