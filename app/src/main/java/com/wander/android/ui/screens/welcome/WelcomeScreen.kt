package com.wander.android.ui.screens.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager
import com.wander.android.core.permissions.rememberPermissionGate
import kotlinx.coroutines.launch

private const val STEP_SOURCES = 0
private const val STEP_RECOGNITION = 1
private const val STEP_COUNT = 2

/**
 * First-run setup, as two steps: where your music comes from, then the one optional capability
 * that needs a download. Everything here is skippable — music on the device works with nothing
 * configured — and every part is reachable again later from Settings.
 */
@Composable
fun WelcomeScreen(
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onDone: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val permissionGate = rememberPermissionGate(
        onAudioGranted = viewModel::refreshLocal,
        requestOnStart = false
    )
    val pager = rememberPagerState(pageCount = { STEP_COUNT })
    val scope = rememberCoroutineScope()

    fun finish() {
        viewModel.finish()
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                when (page) {
                    STEP_SOURCES -> SourcesStep(status, permissionGate::request,
                        onNavidromeLogin, onYouTubeLogin)
                    STEP_RECOGNITION -> RecognitionStep(
                        status.recognitionModel, viewModel::downloadRecognitionModel)
                }
            }
        }

        PageDots(pager.currentPage, STEP_COUNT, Modifier.padding(vertical = 12.dp))

        when (pager.currentPage) {
            STEP_SOURCES -> {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(STEP_RECOGNITION) } },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) { Text("Next") }
                TextButton(
                    onClick = ::finish,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shapes = ButtonDefaults.shapes()
                ) { Text("Skip setup") }
            }
            STEP_RECOGNITION -> {
                Button(
                    onClick = ::finish,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) { Text("Start listening") }
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(STEP_SOURCES) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shapes = ButtonDefaults.shapes()
                ) { Text("Back") }
            }
        }
    }
}

@Composable
private fun SourcesStep(
    status: SetupStatus,
    onGrantLocal: () -> Unit,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit
) {
    Text(text = "Welcome to Wanda", style = MaterialTheme.typography.headlineLarge)
    Text(
        text = "One library, one queue, one player — across your own server, this device, " +
            "YouTube Music and the Internet Archive. No accounts, no telemetry.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    SourceSetupCard(
        title = "Music on this device",
        description = "Plays offline, costs nothing, needs no account.",
        icon = Icons.Rounded.MusicNote,
        isConfigured = status.localGranted,
        actionLabel = "Grant access",
        onAction = onGrantLocal
    )
    SourceSetupCard(
        title = "Navidrome",
        description = "Your own Subsonic-compatible server, at full quality.",
        icon = Icons.Rounded.Cloud,
        isConfigured = status.navidromeConfigured,
        actionLabel = "Sign in",
        onAction = onNavidromeLogin
    )
    SourceSetupCard(
        title = "YouTube Music",
        description = "Signs in through your own session; the cookie never leaves the device.",
        icon = Icons.Rounded.LibraryMusic,
        isConfigured = status.ytMusicConfigured,
        actionLabel = "Sign in",
        onAction = onYouTubeLogin
    )
    SourceSetupCard(
        title = "Internet Archive",
        description = "Live sets and public-domain recordings. Nothing to set up.",
        icon = Icons.Rounded.Public,
        isConfigured = true,
        actionLabel = "",
        onAction = {}
    )
}

@Composable
private fun RecognitionStep(
    model: EmbeddingModelManager.State,
    onDownload: () -> Unit
) {
    Text(text = "Song recognition", style = MaterialTheme.typography.headlineLarge)
    Text(
        text = "Hold your phone to a speaker and Wanda names what's playing — matched against " +
            "your own library, on the device, with nothing sent anywhere.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "It needs a one-time ${EmbeddingModelManager.APPROX_MB} MB model. " +
                "After that it works fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    when (model) {
        is EmbeddingModelManager.State.Downloading -> {
            val fraction by animateFloatAsState(model.fraction, label = "modelDownload")
            Text(
                "Downloading… ${(model.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        EmbeddingModelManager.State.Ready ->
            Text(
                "Downloaded and checked — recognition is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        is EmbeddingModelManager.State.Failed -> {
            Text(
                model.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.padding(top = 8.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text("Try again") }
        }
        EmbeddingModelManager.State.Absent ->
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.padding(top = 4.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text("Download model") }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "You can skip this and download it later from Settings › Fingerprints.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}
