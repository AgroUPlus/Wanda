package com.wander.android.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.data.importer.ImportProgress
import com.wander.android.ui.screens.settings.ImportProgressContent

@Composable
fun ImportProgressStage(
    progress: ImportProgress,
    onOpenPlaylist: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        ImportProgressContent(progress = progress)
        if (progress is ImportProgress.Success) {
            val success = progress
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = {
                        onReset()
                        onOpenPlaylist(success.playlistId)
                    },
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen now")
                }
                FilledTonalButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Done")
                }
            }
        } else if (progress is ImportProgress.Failed) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReset, shapes = ButtonDefaults.shapes()) {
                Text("Try Again")
            }
        }
    }
}

@Composable
fun ImportLoadingOverlay(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Reading playlist tracks...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
