package com.anutej.logseqpocket.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anutej.logseqpocket.AppContainer
import com.anutej.logseqpocket.LogseqPocketApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as LogseqPocketApp).container

/** Creates a ViewModel scoped to the current navigation entry using the app's manual DI container. */
@Composable
inline fun <reified VM : ViewModel> pocketViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
