package com.anutej.logseqpocket.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object JournalsRoute
@Serializable data class BrowseRoute(val tab: Int = 0)
@Serializable data object CardsRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data class PageRoute(val uuid: String, val focusBlock: String? = null)
@Serializable data class TagRoute(val tagKey: String)
@Serializable data class StudyRoute(val deckKey: String)
