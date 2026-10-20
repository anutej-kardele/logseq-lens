package com.anutej.logseqpocket.ui.browse

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.anutej.logseqpocket.data.db.PageEntity
import com.anutej.logseqpocket.ui.pages.PagesList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(initialTab: Int, onOpenPage: (PageEntity) -> Unit, onOpenTag: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Pages") }) }) { padding ->
        PagesList(padding) { onOpenPage(it.page) }
    }
}
