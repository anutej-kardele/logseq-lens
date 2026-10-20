package com.anutej.logseqpocket.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.anutej.logseqpocket.ui.browse.BrowseScreen
import com.anutej.logseqpocket.ui.cards.CardsScreen
import com.anutej.logseqpocket.ui.cards.StudyScreen
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.home.HomeScreen
import com.anutej.logseqpocket.ui.home.Shortcut
import com.anutej.logseqpocket.ui.journals.JournalsScreen
import com.anutej.logseqpocket.ui.reader.PageScreen
import com.anutej.logseqpocket.ui.scratchpad.ScratchpadScreen
import com.anutej.logseqpocket.ui.search.SearchScreen
import com.anutej.logseqpocket.ui.settings.SettingsScreen
import com.anutej.logseqpocket.ui.tags.TagScreen
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class TopLevel(val label: String, val icon: ImageVector, val route: Any, val klass: KClass<*>)

private val topLevel = listOf(
    TopLevel("Home", Icons.Outlined.Home, HomeRoute, HomeRoute::class),
    TopLevel("Journals", Icons.Outlined.CalendarMonth, JournalsRoute, JournalsRoute::class),
    TopLevel("Browse", Icons.Outlined.Tag, BrowseRoute(), BrowseRoute::class),
    TopLevel("Cards", Icons.Outlined.Style, CardsRoute, CardsRoute::class),
    TopLevel("Search", Icons.Outlined.Search, SearchRoute, SearchRoute::class),
)

private fun NavDestination?.isTopLevel() = this?.hierarchy?.any { d -> topLevel.any { d.hasRoute(it.klass) } } == true

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Requests coming from outside the app (widget, launcher shortcut). */
enum class ExternalRequest { Scratchpad, ScratchpadNewItem }

@Composable
fun PocketApp(external: ExternalRequest? = null, onExternalHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    LaunchedEffect(external) {
        when (external) {
            null -> Unit
            ExternalRequest.Scratchpad -> nav.navigate(ScratchpadRoute()) { launchSingleTop = true }
            ExternalRequest.ScratchpadNewItem -> nav.navigate(ScratchpadRoute(newItem = true)) { launchSingleTop = true }
        }
        if (external != null) onExternalHandled()
    }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val onNavigate: (NavRequest) -> Unit = { req ->
        when (req) {
            is NavRequest.Page -> nav.navigate(PageRoute(req.uuid, req.focusBlock))
            is NavRequest.Tag -> nav.navigate(TagRoute(req.tagKey))
            is NavRequest.External -> runCatching { uriHandler.openUri(req.url) }
                .onFailure { scope.launch { snackbar.showSnackbar("No app can open this link") } }
            is NavRequest.Missing -> scope.launch { snackbar.showSnackbar("“${req.label}” isn't in this export") }
        }
    }
    val openPage: (String) -> Unit = { nav.navigate(PageRoute(it)) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (destination.isTopLevel()) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(item.klass) } == true,
                            onClick = { nav.switchTab(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { outer ->
        val bottom = PaddingValues(bottom = outer.calculateBottomPadding())
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenPage = openPage,
                    onShortcut = { s ->
                        when (s) {
                            Shortcut.Journals -> nav.switchTab(JournalsRoute)
                            Shortcut.Pages -> nav.switchTab(BrowseRoute(0))
                            Shortcut.Tags -> nav.navigate(BrowseRoute(1)) {
                                popUpTo(nav.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                            Shortcut.Search -> nav.switchTab(SearchRoute)
                            Shortcut.Cards -> nav.switchTab(CardsRoute)
                        }
                    },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onScratchpad = { nav.navigate(ScratchpadRoute()) },
                )
            }
            composable<JournalsRoute> { JournalsScreen(onOpen = openPage) }
            composable<BrowseRoute> { entry ->
                BrowseScreen(
                    initialTab = entry.toRoute<BrowseRoute>().tab,
                    onOpenPage = { p ->
                        if (p.kind == "tag" || p.kind == "class") onNavigate(NavRequest.Tag(p.uuid)) else openPage(p.uuid)
                    },
                    onOpenTag = { onNavigate(NavRequest.Tag(it)) },
                )
            }
            composable<CardsRoute> { CardsScreen(onStudy = { nav.navigate(StudyRoute(it)) }) }
            composable<SearchRoute> { SearchScreen(onNavigate = onNavigate) }
            composable<ScratchpadRoute> { entry ->
                ScratchpadScreen(startNewItem = entry.toRoute<ScratchpadRoute>().newItem, onBack = { nav.popBackStack() })
            }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<PageRoute> { entry ->
                val r = entry.toRoute<PageRoute>()
                PageScreen(r.uuid, r.focusBlock, onBack = { nav.popBackStack() }, onNavigate = onNavigate)
            }
            composable<TagRoute> { entry ->
                TagScreen(
                    tagKey = entry.toRoute<TagRoute>().tagKey,
                    onBack = { nav.popBackStack() },
                    onNavigate = onNavigate,
                    onStudy = { nav.navigate(StudyRoute(it)) },
                )
            }
            composable<StudyRoute> { entry ->
                StudyScreen(entry.toRoute<StudyRoute>().deckKey, onBack = { nav.popBackStack() }, onNavigate = onNavigate)
            }
        }
    }
}
