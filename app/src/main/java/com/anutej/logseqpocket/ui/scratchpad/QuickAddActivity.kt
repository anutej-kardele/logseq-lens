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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anutej.logseqpocket.LogseqPocketApp
import com.anutej.logseqpocket.data.repo.AppSettings
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.ui.theme.LogseqPocketTheme
import com.anutej.logseqpocket.widget.scratchpadIntent
import kotlinx.coroutines.launch

/**
 * A small sheet that appears over the home screen (launched from the widget or the "New to-do"
 * shortcut) so you can type into the Scratchpad without opening the full app. Each Send appends
 * a line; tapping outside or "Done" returns to wherever you were.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LogseqPocketApp).container
        val asCheckbox = intent.getBooleanExtra(EXTRA_CHECKBOX, false)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val scope = rememberCoroutineScope()
            val added = remember { mutableStateListOf<Pair<String, Boolean>>() }
            LogseqPocketTheme(themeMode = settings.themeMode, readingSize = settings.readingSize) {
                QuickAddSheet(
                    added = added,
                    initialCheckbox = asCheckbox,
                    onAdd = { text, checkbox ->
                        added += text.trim() to checkbox
                        scope.launch { container.scratchpad.update { PadEditor.appendText(it, text, checkbox) } }
                    },
                    onOpenFull = {
                        startActivity(scratchpadIntent(this))
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_CHECKBOX = "checkbox"

        fun intent(context: Context, checkbox: Boolean = false): Intent =
            Intent(context, QuickAddActivity::class.java)
                .putExtra(EXTRA_CHECKBOX, checkbox)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@Composable
private fun QuickAddSheet(
    added: List<Pair<String, Boolean>>,
    initialCheckbox: Boolean,
    onAdd: (String, Boolean) -> Unit,
    onOpenFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
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
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scratchpad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenFull) {
                        Icon(Icons.Outlined.OpenInFull, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open full note")
                    }
                }
                added.takeLast(4).forEach { (text, checkbox) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            if (checkbox) Icons.Outlined.CheckBoxOutlineBlank else Icons.Outlined.CheckBox,
                            null,
                            Modifier.size(14.dp),
                            tint = if (checkbox) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                QuickAddField(
                    onAdd = onAdd,
                    initialCheckbox = initialCheckbox,
                    placeholder = "Type a thought…",
                    focusRequester = focus,
                )
                Row {
                    Text(
                        if (added.isEmpty()) "Saved to this phone only" else "Added ${added.size} · saved to this phone only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}
