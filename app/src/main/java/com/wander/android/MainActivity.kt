package com.wander.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.sources.agro.explain
import com.wander.android.data.repository.LinkRepository
import com.wander.android.data.sources.agro.AgroHandoffPublisher
import com.wander.android.ui.WanderApp
import com.wander.android.ui.navigation.DeepLinkRouter
import com.wander.android.ui.navigation.Routes
import com.wander.android.ui.theme.WanderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds no state of its own. It connects to [com.wander.android.core.playback.PlaybackService]
 * while visible and hands everything else to [WanderApp]; ViewModels come from Hilt, so they
 * survive configuration changes (the previous `remember {}` construction did not).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var agroClient: AgroClient
    @Inject lateinit var agroHandoffPublisher: AgroHandoffPublisher
    @Inject lateinit var linkRepository: LinkRepository
    @Inject lateinit var deepLinkRouter: DeepLinkRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        // Announces the device once per launch. Without it the server only ever heard from Wanda
        // at pairing time, so a device that had been restarted looked gone.
        agroHandoffPublisher.register()

        setContent {
            val amoled by secureStorage.isAmoledBlack.collectAsStateWithLifecycle()
            val monet by secureStorage.isMonetDynamic.collectAsStateWithLifecycle()

            WanderTheme(dynamicColor = monet, amoledBlack = amoled) {
                WanderApp(playerConnection = playerConnection)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Links arrive here: an `agro:` pairing QR, a YouTube/YouTube Music track
     * someone shared, a `wanda://listen` handoff, a `wanda://inbox` notification,
     * or a Jam invite link `https://frwd.top/jam?code=...` / `wanda://jam?code=...`.
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        when {
            uri.scheme == "agro" -> handleAgroPairing(uri)
            // A tapped drop notification.
            uri.scheme == "wanda" && uri.host == "inbox" -> deepLinkRouter.request(Routes.INBOX)
            isJamLink(uri) -> handleJamLink(uri)
            linkRepository.canOpen(uri) -> openSharedLink(uri)
            uri.scheme == "https" || uri.scheme == "wanda" -> Toast.makeText(
                this,
                "That link isn't a track or Jam Wanda can open.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isJamLink(uri: Uri): Boolean {
        val isScheme = uri.scheme in setOf("wanda", "https", "http")
        val isJamHostOrPath = uri.host == "jam" || uri.pathSegments.contains("jam")
        val hasCodeOrId = uri.getQueryParameter("code") != null || uri.getQueryParameter("id") != null
        return isScheme && isJamHostOrPath && hasCodeOrId
    }

    private fun handleJamLink(uri: Uri) {
        val code = uri.getQueryParameter("code")?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }?.take(10)
        if (!code.isNullOrEmpty()) {
            Toast.makeText(this, "Opening Jam $code...", Toast.LENGTH_SHORT).show()
            deepLinkRouter.request(Routes.jam(code))
        } else {
            deepLinkRouter.request(Routes.jam())
        }
    }

    /**
     * Resolves the link and plays it. Failures are reported: a tapped link that silently does
     * nothing is indistinguishable from the app having crashed on open.
     */
    private fun openSharedLink(uri: Uri) {
        lifecycleScope.launch {
            linkRepository.resolve(uri).fold(
                onSuccess = { playerConnection.play(listOf(it)) },
                onFailure = {
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "Couldn't open that link.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun handleAgroPairing(uri: Uri) {
        lifecycleScope.launch {
            val result = agroClient.parseQrCodePayload(uri.toString())
            val message = result.fold(
                onSuccess = { petname ->
                    "Paired with Agro as ${petname ?: secureStorage.agroDevicePetname.ifEmpty { "wanda" }}"
                },
                onFailure = { error ->
                    // The message only; the URL and token in this flow are credentials.
                    android.util.Log.w("Wanda", "Agro pairing failed: ${error.message}")
                    AgroAuthError.from(error).explain()
                }
            )
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }
}
