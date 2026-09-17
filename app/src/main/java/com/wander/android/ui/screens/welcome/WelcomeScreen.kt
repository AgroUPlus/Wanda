package com.wander.android.ui.screens.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager
import com.wander.android.core.permissions.rememberPermissionGate
import kotlinx.coroutines.launch

private const val STEP_SOURCES = 0
private const val STEP_RECOGNITION = 1
private const val STEP_GESTURES = 2
private const val STEP_COUNT = 3

/**
 * First-run setup, as three steps: where your music comes from, the one optional capability that
 * needs a download, and how the player is driven. Everything here is skippable — music on the
 * device works with nothing configured — and every part is reachable again later from Settings.
 *
 * Gestures come last because they are the only step that teaches rather than asks: the two before
 * it want a decision, and putting the thing with no buttons to press between them would read as a
 * step that had been skipped.
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
                    STEP_GESTURES -> GesturesStep()
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
                ) { Text(stringResource(R.string.common_next)) }
                TextButton(
                    onClick = ::finish,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.welcome_skip_setup)) }
            }
            STEP_RECOGNITION -> {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(STEP_GESTURES) } },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.common_next)) }
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(STEP_SOURCES) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.common_back)) }
            }
            STEP_GESTURES -> {
                Button(
                    onClick = ::finish,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.welcome_start_listening)) }
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(STEP_RECOGNITION) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.common_back)) }
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
    Text(text = stringResource(R.string.welcome_welcome_wanda), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_one_library_one_queue_one),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    SourceSetupCard(
        title = stringResource(R.string.common_music_device),
        description = stringResource(R.string.welcome_plays_offline_costs_nothing_needs),
        icon = Icons.Rounded.MusicNote,
        isConfigured = status.localGranted,
        actionLabel = "Grant access",
        onAction = onGrantLocal
    )
    SourceSetupCard(
        title = stringResource(R.string.common_navidrome),
        description = stringResource(R.string.welcome_own_subsonic_compatible_server_full),
        icon = Icons.Rounded.Cloud,
        isConfigured = status.navidromeConfigured,
        actionLabel = "Sign in",
        onAction = onNavidromeLogin
    )
    SourceSetupCard(
        title = stringResource(R.string.common_youtube_music),
        description = stringResource(R.string.welcome_signs_through_own_session_cookie),
        icon = Icons.Rounded.LibraryMusic,
        isConfigured = status.ytMusicConfigured,
        actionLabel = "Sign in",
        onAction = onYouTubeLogin
    )
}

@Composable
private fun RecognitionStep(
    model: EmbeddingModelManager.State,
    onDownload: () -> Unit
) {
    Text(text = stringResource(R.string.welcome_song_recognition), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_hold_phone_speaker_wanda_names),
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
            ) { Text(stringResource(R.string.common_try_again)) }
        }
        EmbeddingModelManager.State.Absent ->
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.padding(top = 4.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.welcome_download_model)) }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.welcome_can_skip_download_later_from),
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
