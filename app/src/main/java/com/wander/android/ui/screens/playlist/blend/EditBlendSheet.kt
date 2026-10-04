package com.wander.android.ui.screens.playlist.blend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.data.sources.agro.BlendRecipe
import com.wander.android.ui.components.WandaSheet
import com.wander.android.ui.components.newplaylist.BlendRecipeControls

/** The creator's view of a blend's recipe: its name, size, mix, reach and refresh. */
@Composable
internal fun EditBlendSheet(blend: BlendInfo, onSave: (String, BlendRecipe) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(blend.title) }
    var recipe by remember { mutableStateOf(blend.recipe) }
    WandaSheet(onDismissRequest = onDismiss) { dismiss ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(stringResource(R.string.blend_edit_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.common_name)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
            BlendRecipeControls(recipe) { recipe = it }
            Button(
                onClick = {
                    onSave(title.trim(), recipe)
                    dismiss()
                },
                enabled = title.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.blend_edit_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
