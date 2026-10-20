package com.anutej.logseqpocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.ui.nav.PocketApp
import com.anutej.logseqpocket.ui.theme.LogseqPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LogseqPocketApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            LogseqPocketTheme(themeMode = settings.themeMode, readingSize = settings.readingSize) {
                PocketApp()
            }
        }
    }
}
