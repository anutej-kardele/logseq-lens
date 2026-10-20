package com.anutej.logseqpocket.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.reflect.KClass

private data class TopLevel(val label: String, val icon: ImageVector, val route: Any, val klass: KClass<*>)

private val topLevel = listOf(
    TopLevel("Home", Icons.Outlined.Home, HomeRoute, HomeRoute::class),
    TopLevel("Journals", Icons.Outlined.CalendarMonth, JournalsRoute, JournalsRoute::class),
    TopLevel("Browse", Icons.Outlined.Tag, BrowseRoute, BrowseRoute::class),
    TopLevel("Cards", Icons.Outlined.Style, CardsRoute, CardsRoute::class),
    TopLevel("Search", Icons.Outlined.Search, SearchRoute, SearchRoute::class),
)

@Composable
fun PocketApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevel.forEach { item ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(item.klass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> { Placeholder("Home") }
            composable<JournalsRoute> { Placeholder("Journals") }
            composable<BrowseRoute> { Placeholder("Browse") }
            composable<CardsRoute> { Placeholder("Cards") }
            composable<SearchRoute> { Placeholder("Search") }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
