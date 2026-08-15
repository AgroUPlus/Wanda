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
import com.wander.android.data.sources.agro.AgroClient
import com.wander.android.data.repository.LinkRepository
import com.wander.android.data.sources.agro.AgroHandoffPublisher
import com.wander.android.ui.WanderApp
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
     * Two kinds of link arrive here: an `agro:` pairing QR, and a YouTube/YouTube Music track
     * someone shared. Anything else is left alone.
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        when {
            uri.scheme == "agro" -> handleAgroPairing(uri)
            linkRepository.canOpen(uri) -> openSharedLink(uri)
            // Wanda offers itself for every YouTube link, but only *track* links have anything
            // behind them here. Landing silently on Home would read as the app having failed to
            // open the thing that was tapped.
            uri.scheme == "https" -> Toast.makeText(
                this,
                "That YouTube link isn't a track Wanda can play.",
                Toast.LENGTH_LONG
            ).show()
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
            val success = agroClient.parseQrCodePayload(uri.toString())
            // A silent failure here read as "paired" to the user, who then waited for a sync
            // that was never going to come.
            val message = if (success) {
                "Paired with Agro as ${secureStorage.agroDevicePetname.ifEmpty { "wanda" }}"
            } else {
                "Agro pairing failed — check the server is reachable and rescan"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }
}
