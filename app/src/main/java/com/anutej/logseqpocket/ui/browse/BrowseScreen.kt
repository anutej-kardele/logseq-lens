package com.anutej.logseqpocket.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.anutej.logseqpocket.data.db.PageEntity
import com.anutej.logseqpocket.ui.pages.PagesList
import com.anutej.logseqpocket.ui.tags.TagsList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(initialTab: Int, onOpenPage: (PageEntity) -> Unit, onOpenTag: (String) -> Unit) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Browse") }) }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pages") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tags & classes") })
            }
            val inner = PaddingValues(bottom = padding.calculateBottomPadding())
            if (tab == 0) PagesList(inner) { onOpenPage(it.page) } else TagsList(inner, onOpenTag)
        }
    }
}
