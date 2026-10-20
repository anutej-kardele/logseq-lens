package com.anutej.logseqpocket

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.ui.nav.ExternalRequest
import com.anutej.logseqpocket.ui.nav.PocketApp
import com.anutej.logseqpocket.ui.theme.LogseqPocketTheme
import com.anutej.logseqpocket.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val external = MutableStateFlow<ExternalRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        val container = (application as LogseqPocketApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val request by external.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/navigation bar icons legible when the in-app theme differs from the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            LogseqPocketTheme(themeMode = settings.themeMode, readingSize = settings.readingSize) {
                PocketApp(request, onExternalHandled = { external.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        external.value = when (intent?.action) {
            ACTION_SCRATCHPAD -> ExternalRequest.Scratchpad
            ACTION_SCRATCHPAD_NEW_ITEM -> ExternalRequest.ScratchpadNewItem
            else -> external.value
        }
    }

    companion object {
        const val ACTION_SCRATCHPAD = "com.anutej.logseqpocket.action.SCRATCHPAD"
        const val ACTION_SCRATCHPAD_NEW_ITEM = "com.anutej.logseqpocket.action.SCRATCHPAD_NEW_ITEM"
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
