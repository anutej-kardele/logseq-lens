package com.anutej.logseqpocket.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.anutej.logseqpocket.LogseqPocketApp
import com.anutej.logseqpocket.MainActivity
import com.anutej.logseqpocket.domain.scratchpad.PadDoc
import com.anutej.logseqpocket.domain.scratchpad.PadEditor
import com.anutej.logseqpocket.domain.scratchpad.PadLine
import com.anutej.logseqpocket.ui.theme.DarkScheme
import com.anutej.logseqpocket.ui.theme.LightScheme

private val widgetColors = ColorProviders(light = LightScheme, dark = DarkScheme)
private val LineIdKey = ActionParameters.Key<Long>("lineId")

/**
 * Home-screen view of the Scratchpad. Checkboxes can be ticked right here; typing happens in the app
 * (Android widgets can't host text fields), so tapping text or "+" opens the editor.
 */
class ScratchpadWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = (context.applicationContext as LogseqPocketApp).container.scratchpad
        val initial = store.current()
        provideContent {
            val doc by store.doc.collectAsState(initial)
            GlanceTheme(colors = widgetColors) { WidgetBody(context, doc) }
        }
    }
}

class ScratchpadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScratchpadWidget()
}

/** Persists a tick from the widget. The new state comes from the CheckBox itself. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LineIdKey] ?: return
        val checked = parameters[ToggleableStateKey] ?: return
        val store = (context.applicationContext as LogseqPocketApp).container.scratchpad
        store.update { PadEditor.setChecked(it, id, checked) }
        ScratchpadWidget().update(context, glanceId)
    }
}

fun scratchpadIntent(context: Context, newItem: Boolean = false): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(if (newItem) MainActivity.ACTION_SCRATCHPAD_NEW_ITEM else MainActivity.ACTION_SCRATCHPAD)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun WidgetBody(context: Context, doc: PadDoc) {
    val open = actionStartActivity(scratchpadIntent(context))
    val lines = doc.lines.dropLastWhile { it.text.isBlank() && !it.checkbox }
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(
                    "Scratchpad",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
                if (doc.openItems > 0) {
                    Text(
                        "${doc.openItems} open",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
            }
            Box(
                GlanceModifier.size(36.dp).cornerRadius(18.dp).background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionStartActivity(scratchpadIntent(context, newItem = true))),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
        }
        if (lines.isEmpty()) {
            Box(GlanceModifier.fillMaxSize().clickable(open), contentAlignment = Alignment.Center) {
                Text("Tap to jot something down", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(lines, itemId = { it.id }) { line -> WidgetLine(line, open) }
            }
        }
    }
}

@Composable
private fun WidgetLine(line: PadLine, open: androidx.glance.action.Action) {
    if (line.checkbox) {
        CheckBox(
            checked = line.checked,
            onCheckedChange = actionRunCallback<ToggleItemAction>(actionParametersOf(LineIdKey to line.id)),
            text = line.text.ifEmpty { " " },
            style = TextStyle(
                color = if (line.checked) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 3,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    } else if (line.text.isBlank()) {
        Spacer(GlanceModifier.height(8.dp))
    } else {
        Text(
            line.text,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 4,
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).clickable(open),
        )
    }
}
