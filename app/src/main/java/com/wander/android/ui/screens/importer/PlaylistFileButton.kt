package com.wander.android.ui.screens.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Opens an `.m3u` / `.m3u8` file and hands its text to the paste box, where it is read like any
 * other pasted playlist. Android often reports these files as `application/octet-stream`, so the
 * picker cannot be narrowed to the playlist types alone.
 */
@Composable
internal fun PlaylistFileButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val out = StringBuilder()
                        val buffer = CharArray(8 * 1024)
                        while (true) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            out.append(buffer, 0, read)
                            if (out.length > MAX_FILE_CHARS) return@use null
                        }
                        out.toString()
                    }
                }
                if (text == null) error = R.string.importer_file_too_large else onText(text)
            } catch (_: IOException) {
                error = R.string.importer_file_unreadable
            }
        }
    }

    OutlinedButton(
        onClick = { launcher.launch(PLAYLIST_MIME_TYPES) },
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.importer_open_playlist_file))
    }
    error?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** About ten thousand tracks of M3U: far past any playlist, well short of exhausting memory. */
private const val MAX_FILE_CHARS = 1_000_000

private val PLAYLIST_MIME_TYPES = arrayOf(
    "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl",
    "text/plain", "application/octet-stream"
)
