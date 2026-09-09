package com.wander.android.core.audio.recognition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.repository.IndexReadiness
import com.wander.android.data.repository.Recognition
import com.wander.android.data.repository.RecognitionEngine
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.listen.ListenState
import com.wander.android.ui.components.listen.ListenViewModel
import com.wander.android.ui.components.listen.PulsingMic
import com.wander.android.ui.theme.WanderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecognitionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WanderTheme {
                RecognitionScreen(
                    onDismiss = { finish() },
                    onOpenTrack = { finish() }
                )
            }
        }
    }
}

@Composable
fun RecognitionScreen(
    onDismiss: () -> Unit,
    onOpenTrack: () -> Unit,
    viewModel: ListenViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.92f))
                },
                label = "listenState"
            ) { current ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (current) {
                        ListenState.Idle, ListenState.Listening ->
                            ListeningView(readiness, audioLevel)
                        ListenState.Identifying -> IdentifyingView(readiness)
                        is ListenState.Matched -> MatchedView(
                            recognition = current.recognition,
                            onPlay = {
                                viewModel.playMatch()
                                onOpenTrack()
                            }
                        )
                        ListenState.NoMatch -> NoMatchView(readiness, onRetry = viewModel::start)
                        ListenState.Failed -> FailedView(onRetry = viewModel::start)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningView(readiness: IndexReadiness, audioLevel: Float = 0f) {
    PulsingMic(audioLevel = audioLevel)

    Text("Listening…", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(
        text = when (readiness) {
            is IndexReadiness.Ready ->
                "Hold it near the music. Matching against ${readiness.trackCount} " +
                    "${if (readiness.trackCount == 1) "track" else "tracks"} measured on this device."
            IndexReadiness.Empty -> "Index is filling..."
            IndexReadiness.ModelMissing -> "Model missing"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun IdentifyingView(readiness: IndexReadiness) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp), color = Color.White)
    }

    Text("Identifying…", style = MaterialTheme.typography.headlineSmall, color = Color.White)
}

@Composable
private fun MatchedView(recognition: Recognition, onPlay: () -> Unit) {
    Artwork(
        url = recognition.track.artworkUrl,
        contentDescription = recognition.track.title,
        sizeDp = 140.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.size(140.dp)
    )
    Text(
        text = recognition.track.title,
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = recognition.track.artist,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) {
        if (recognition.engine == RecognitionEngine.MELODY) Text("Play")
        else Text("Play")
    }
}

@Composable
private fun NoMatchView(readiness: IndexReadiness, onRetry: () -> Unit) {
    Text("No match", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Listen again", color = Color.White) }
}

@Composable
private fun FailedView(onRetry: () -> Unit) {
    Icon(
        imageVector = Icons.Rounded.MicOff,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.size(56.dp)
    )
    Text("Could not listen", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text("Try again", color = Color.White) }
}
