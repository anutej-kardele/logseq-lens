package com.anutej.logseqpocket.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun Pending(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$name — coming in Milestone 3") }
}
