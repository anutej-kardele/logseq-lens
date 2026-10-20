package com.anutej.logseqpocket.ui.scratchpad

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.LogseqPocketApp
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.ui.nav.pocketViewModel
import com.anutej.logseqpocket.ui.theme.LogseqPocketTheme
import com.anutej.logseqpocket.widget.scratchpadIntent

/**
 * The Scratchpad floating over the home screen, opened by tapping the widget: the same note,
 * editable in place with the keyboard up and the cursor where you tapped. Tap outside to close.
 */
class FloatingScratchpadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LogseqPocketApp).container
        val lineId = intent.getLongExtra(EXTRA_LINE_ID, -1L).takeIf { it >= 0 }
        val newItem = intent.getBooleanExtra(EXTRA_NEW_ITEM, false)
        val firstLaunch = savedInstanceState == null
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val vm = pocketViewModel { ScratchpadViewModel(it.scratchpad) }
            LaunchedEffect(Unit) {
                if (firstLaunch) { if (newItem) vm.appendCheckbox() else vm.focusWhenLoaded(lineId) }
            }
            LogseqPocketTheme(themeMode = settings.themeMode, readingSize = settings.readingSize) {
                FloatingNote(
                    vm = vm,
                    onOpenFull = { startActivity(scratchpadIntent(this)); finish() },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_LINE_ID = "lineId"
        private const val EXTRA_NEW_ITEM = "newItem"

        fun intent(context: Context, lineId: Long? = null, newItem: Boolean = false): Intent =
            Intent(context, FloatingScratchpadActivity::class.java)
                .putExtra(EXTRA_LINE_ID, lineId ?: -1L)
                .putExtra(EXTRA_NEW_ITEM, newItem)
                // Distinct data per line so the widget's PendingIntents don't collapse into one.
                .setData(android.net.Uri.parse("logseqpocket://scratchpad/${lineId ?: if (newItem) "new" else "end"}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@Composable
private fun FloatingNote(vm: ScratchpadViewModel, onOpenFull: () -> Unit, onDismiss: () -> Unit) {
    val doc by vm.doc.collectAsStateWithLifecycle()
    var focusedLine by remember { mutableStateOf<Long?>(null) }
    // Whole sheet ≤ ~33% of the screen; it sits right above the keyboard (imePadding).
    val maxHeight = (LocalConfiguration.current.screenHeightDp * FLOATING_FRACTION).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.navigationBarsPadding().heightIn(max = maxHeight).padding(top = 4.dp)) {
                Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenFull) { Icon(Icons.Outlined.OpenInFull, "Open full note", Modifier.size(18.dp)) }
                }
                val d = doc ?: return@Column
                PadNoteEditor(
                    doc = d,
                    vm = vm,
                    onFocusedLine = { focusedLine = it },
                    tailHeight = 24.dp,
                    placeholder = "Thoughts, to-dos… ([] for a checkbox)",
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
                HorizontalDivider()
                PadEditToolbar(focusedLine ?: d.lines.last().id, vm, Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
