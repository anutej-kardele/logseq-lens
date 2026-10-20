package com.anutej.logseqpocket.ui.tags

import androidx.compose.runtime.Composable
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.Pending

@Composable
fun TagScreen(tagKey: String, onBack: () -> Unit, onNavigate: (NavRequest) -> Unit, onStudy: (String) -> Unit) = Pending("Tag view")
