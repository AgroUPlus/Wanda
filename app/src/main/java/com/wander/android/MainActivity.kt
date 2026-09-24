package com.wander.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wander.android.core.i18n.AppLocaleStore
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroHandoffPublisher
import com.wander.android.ui.WanderApp
import com.wander.android.ui.theme.WanderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Holds no state of its own. It connects to [com.wander.android.core.playback.PlaybackService]
 * while visible and hands everything else to [WanderApp]; ViewModels come from Hilt, so they
 * survive configuration changes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var secureStorage: SecureStorage
    @Inject internal lateinit var agroHandoffPublisher: AgroHandoffPublisher
    @Inject internal lateinit var intentHandler: AppIntentHandler

    /**
     * Applies the chosen display language before a single resource is resolved.
     *
     * Only does anything below API 33, where the platform has no per-app language of its own —
     * see [AppLocaleStore.wrap].
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intentHandler.handleIntent(lifecycleScope, intent)
        // Announces the device once per launch. Without it the server only ever heard from Wanda
        // at pairing time, so a device that had been restarted looked gone.
        agroHandoffPublisher.register()

        setContent {
            val amoled by secureStorage.isAmoledBlack.collectAsStateWithLifecycle()
            val monet by secureStorage.isMonetDynamic.collectAsStateWithLifecycle()
            val reduceMotion by secureStorage.isReduceMotion.collectAsStateWithLifecycle()

            WanderTheme(dynamicColor = monet, amoledBlack = amoled, reduceMotion = reduceMotion) {
                WanderApp(playerConnection = playerConnection)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentHandler.handleIntent(lifecycleScope, intent)
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }
}
