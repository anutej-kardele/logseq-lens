package com.anutej.logseqpocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anutej.logseqpocket.ui.nav.PocketApp
import com.anutej.logseqpocket.ui.theme.LogseqPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LogseqPocketTheme {
                PocketApp()
            }
        }
    }
}
