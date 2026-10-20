package com.anutej.logseqpocket.ui.cards

import androidx.compose.runtime.Composable
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.Pending

@Composable fun CardsScreen(onStudy: (String) -> Unit) = Pending("Flashcards")
@Composable fun StudyScreen(deckKey: String, onBack: () -> Unit, onNavigate: (NavRequest) -> Unit) = Pending("Study")
