package com.wander.android.ui.screens.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.WebViewLifecycle
import com.wander.android.ui.components.release

private const val DEEZER_LOGIN_URL = "https://www.deezer.com/en/login"
private const val DEEZER_DOMAIN_COOKIE = "https://www.deezer.com"

/**
 * Signs in to Deezer in an embedded WebView and intercepts the resulting `arl` session cookie.
 * Also provides a manual ARL token input field.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
    onDone: () -> Unit,
    viewModel: DeezerLoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    WebViewLifecycle(webViewInstance)

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onDone()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Text(
            text = stringResource(R.string.login_sign_deezer),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        AndroidView(
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    webViewInstance = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val cookie = CookieManager.getInstance().getCookie(DEEZER_DOMAIN_COOKIE)
                            if (cookie != null && cookie.contains("arl=")) {
                                viewModel.onSessionCaptured(cookie)
                            }
                        }
                    }
                    loadUrl(DEEZER_LOGIN_URL)
                }
            },
            onRelease = { webView ->
                webViewInstance = null
                webView.release()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        HorizontalDivider()

        OutlinedTextField(
            value = state.manualArl,
            onValueChange = viewModel::onManualArlChange,
            label = { Text(stringResource(R.string.login_paste_arl)) },
            isError = state.error != null,
            supportingText = state.error?.let { msg -> { Text(msg) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        TextButton(
            onClick = viewModel::submitManualArl,
            enabled = state.manualArl.isNotBlank() && !state.isLoading,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(stringResource(R.string.login_use_pasted_arl))
        }
    }
}
