package com.wander.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wander.android.core.database.DatabaseCompatibility
import com.wander.android.core.i18n.AppLocaleStore
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroHandoffPublisher
import com.wander.android.ui.WanderApp
import com.wander.android.ui.screens.startup.DatabaseTooOldScreen
import com.wander.android.ui.theme.ThemeMode
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

    /** Set when the database is too old to open; nothing that would query it may run. */
    private var databaseTooOld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        databaseTooOld = DatabaseCompatibility.isTooOld(this)
        if (!databaseTooOld) {
            intentHandler.handleIntent(lifecycleScope, intent)
            // Announces the device once per launch. Without it the server only ever heard from Wanda
            // at pairing time, so a device that had been restarted looked gone.
            agroHandoffPublisher.register()
        }

        setContent {
            val amoled by secureStorage.isAmoledBlack.collectAsStateWithLifecycle()
            val monet by secureStorage.isMonetDynamic.collectAsStateWithLifecycle()
            val reduceMotion by secureStorage.isReduceMotion.collectAsStateWithLifecycle()
            val themeMode by secureStorage.displayPrefs.themeMode.collectAsStateWithLifecycle()
            val darkTheme = ThemeMode.fromKey(themeMode).isDark()

            // The bar icons follow the app's theme, not the phone's: with a forced Light theme on
            // a dark phone they would otherwise be white on white.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme }
                )
                onDispose { }
            }

            WanderTheme(
                dynamicColor = monet,
                amoledBlack = amoled,
                darkTheme = darkTheme,
                reduceMotion = reduceMotion
            ) {
                if (databaseTooOld) DatabaseTooOldScreen() else WanderApp(playerConnection = playerConnection)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!databaseTooOld) intentHandler.handleIntent(lifecycleScope, intent)
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    private companion object {
        /** `enableEdgeToEdge`'s own default navigation-bar scrims, which it does not expose. */
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
