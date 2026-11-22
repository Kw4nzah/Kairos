package com.kairos.app

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairos.app.data.ThemeMode
import com.kairos.app.ui.KairosRoot
import com.kairos.app.ui.KairosTheme
import com.kairos.app.ui.MainViewModel
import com.kairos.app.ui.SplashOverlay
import com.kairos.app.widget.ACTION_NEW

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings are read, so the chosen theme never flashes the wrong one.
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        // The Compose splash overlay takes over with the same look, so the system one just goes away.
        splash.setOnExitAnimationListener { provider -> provider.remove() }
        vm.syncLanguageFromSystem()
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val system = isSystemInDarkTheme()
            val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> system
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icon colours follow the app theme, not only the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }
            KairosTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box {
                    KairosRoot(vm)
                    SplashOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == ACTION_NEW) vm.newRequested.value = true
    }
}
