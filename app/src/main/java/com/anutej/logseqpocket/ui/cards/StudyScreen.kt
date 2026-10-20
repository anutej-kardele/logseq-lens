package com.anutej.logseqpocket.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.domain.cards.ReviewResult
import com.anutej.logseqpocket.domain.cards.StudySession
import com.anutej.logseqpocket.ui.components.EmptyState
import com.anutej.logseqpocket.ui.components.LoadingState
import com.anutej.logseqpocket.ui.components.NavRequest
import com.anutej.logseqpocket.ui.components.NoteContent
import com.anutej.logseqpocket.ui.components.PropertyRow
import com.anutej.logseqpocket.ui.components.RenderContext
import com.anutej.logseqpocket.ui.components.TagRow
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.reader.BlockRow
import com.anutej.logseqpocket.ui.theme.PocketTheme
import com.anutej.logseqpocket.ui.theme.noteBodyStyle
import com.anutej.logseqpocket.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(deckKey: String, onBack: () -> Unit, onNavigate: (NavRequest) -> Unit) {
    val vm = pocketViewModel(key = "study:$deckKey") { StudyViewModel(it.study, it.notes, deckKey) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val session = ui.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.deck?.title ?: "Flashcards", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (session != null && session.total > 0) {
                            val pos = (session.position + 1).coerceAtMost(session.total)
                            Text(
                                (if (session.againOnly) "Review again · " else "") + "$pos / ${session.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Session options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem({ Text("Shuffle") }, { vm.shuffle(); menu = false }, leadingIcon = { Icon(Icons.Outlined.Shuffle, null) })
                        DropdownMenuItem({ Text("In original order") }, { vm.inOrder(); menu = false }, leadingIcon = { Icon(Icons.Outlined.FormatListNumbered, null) })
                        DropdownMenuItem({ Text("Restart session") }, { vm.restart(); menu = false }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
                        DropdownMenuItem({ Text("Review “again” cards") }, { vm.reviewAgain(); menu = false }, leadingIcon = { Icon(Icons.Outlined.Replay, null) })
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> LoadingState()
                ui.deck == null || session == null || session.total == 0 -> EmptyState(
                    Icons.Outlined.Style, "No cards in this deck", "This category has no flashcards in the current export.",
                )
                session.isFinished -> SessionSummary(session, vm::reviewAgain, vm::restart, vm::shuffle, onBack)
                else -> {
                    val card = ui.card
                    if (card == null) LoadingState() else {
                        StudyCard(
                            ui = ui,
                            card = card,
                            session = session,
                            onFlip = vm::flip,
                            onPrev = vm::previous,
                            onNext = vm::next,
                            onMark = vm::mark,
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyCard(
    ui: StudyUi,
    card: CardView,
    session: StudySession,
    onFlip: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMark: (ReviewResult) -> Unit,
    onNavigate: (NavRequest) -> Unit,
) {
    val colors = PocketTheme.colors
    val rotation by animateFloatAsState(if (ui.flipped) 180f else 0f, tween(320), label = "flip")
    val showBack = rotation > 90f
    val frontCtx = remember(ui.refs, card) { RenderContext(ui.refs, onNavigate = onNavigate, revealCloze = false) }
    val backCtx = remember(ui.refs, card) { RenderContext(ui.refs, onNavigate = onNavigate, revealCloze = true) }
    val body = noteBodyStyle(PocketTheme.readingSize)

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { session.answered.toFloat() / session.total },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Known ${session.knownCount}", style = MaterialTheme.typography.labelMedium, color = colors.know)
            Text("Again ${session.againCount}", style = MaterialTheme.typography.labelMedium, color = colors.again)
            Spacer(Modifier.weight(1f))
            card.progress?.let {
                Text(
                    "Reviewed ${it.reviewCount}× · last ${it.lastResult?.lowercase() ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The card. Tapping anywhere on it flips; long answers scroll inside.
        Surface(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onFlip)
                .semantics {
                    stateDescription = if (ui.flipped) "Showing answer" else "Showing question"
                    onClick("Flip card") { onFlip(); true }
                },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 1.dp,
        ) {
            Box(Modifier.graphicsLayer { rotationY = if (showBack) 180f else 0f }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 22.dp)) {
                    Text(
                        if (card.row.pageKind == "journal") Format.journalShort(card.row.journalDay, card.row.pageTitle) else card.row.pageTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    NoteContent(
                        card.front,
                        if (showBack) backCtx else frontCtx,
                        Modifier.padding(horizontal = 22.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    )
                    if (card.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        TagRow(card.tags.map { it.name to null }, Modifier.padding(horizontal = 22.dp))
                    }
                    if (!showBack) {
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "Tap to reveal the answer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        HorizontalDivider(Modifier.padding(horizontal = 22.dp, vertical = 16.dp))
                        if (card.answer.none { it.block.content.isNotBlank() }) {
                            Text(
                                "No answer in the export — this card has no nested blocks in Logseq.",
                                style = body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 22.dp),
                            )
                        } else {
                            val offset = card.row.depth + 1
                            card.answer.forEach { row ->
                                BlockRow(
                                    row = row,
                                    ctx = backCtx,
                                    onToggle = {},
                                    onTagClick = { onNavigate(NavRequest.Tag(it)) },
                                    highlighted = false,
                                    depthOffset = offset,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        if (card.reviewInfo.isNotEmpty()) PropertyRow(card.reviewInfo, Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                        TextButton(
                            onClick = { onNavigate(NavRequest.Page(card.row.pageUuid, card.row.blockUuid)) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open in note")
                        }
                    }
                }
            }
        }
        // Controls
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onPrev, enabled = session.position > 0) { Icon(Icons.Outlined.ChevronLeft, "Previous card") }
            if (!ui.flipped) {
                Button(onClick = onFlip, modifier = Modifier.weight(1f).height(52.dp)) { Text("Show answer") }
            } else {
                OutlinedButton(
                    onClick = { onMark(ReviewResult.Again) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.again),
                ) { Icon(Icons.Outlined.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Again", maxLines = 1) }
                Button(
                    onClick = { onMark(ReviewResult.Know) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.know),
                ) { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Know") }
            }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, "Next card") }
        }
    }
}

@Composable
private fun SessionSummary(
    session: StudySession,
    onReviewAgain: () -> Unit,
    onRestart: () -> Unit,
    onShuffle: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = PocketTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Session complete", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${session.answered} of ${session.total} cards answered",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${session.knownCount}", style = MaterialTheme.typography.headlineMedium, color = colors.know)
                Text("Known", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${session.againCount}", style = MaterialTheme.typography.headlineMedium, color = colors.again)
                Text("Review again", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (session.againCount > 0) {
            Button(onClick = onReviewAgain, modifier = Modifier.fillMaxWidth()) { Text("Review ${session.againCount} “again” card${if (session.againCount == 1) "" else "s"}") }
        }
        FilledTonalButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Restart session") }
        OutlinedButton(onClick = onShuffle, modifier = Modifier.fillMaxWidth()) { Text("New shuffled session") }
        TextButton(onClick = onDone) { Text("Done") }
    }
}
