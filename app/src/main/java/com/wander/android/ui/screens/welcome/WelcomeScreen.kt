package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 *
 * Just the pager shell and its footer — each step is its own file: [SourcesStep], [RecognitionStep],
 * [GesturesStep].
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
                        onNavidromeLogin, onYouTubeLogin, onImportedBackup = ::finish)
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
