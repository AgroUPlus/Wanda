package com.wander.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.ui.WanderApp
import com.wander.android.ui.theme.WanderTheme
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val amoled by secureStorage.isAmoledBlack.collectAsStateWithLifecycle()
            val monet by secureStorage.isMonetDynamic.collectAsStateWithLifecycle()

            WanderTheme(dynamicColor = monet, amoledBlack = amoled) {
                WanderApp(playerConnection = playerConnection)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }
}
