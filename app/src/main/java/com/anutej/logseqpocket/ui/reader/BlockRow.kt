package com.anutej.logseqpocket.ui.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.ui.components.NoteContent
import com.anutej.logseqpocket.ui.components.PropertyRow
import com.anutej.logseqpocket.ui.components.RenderContext
import com.anutej.logseqpocket.ui.components.TagRow
import com.anutej.logseqpocket.ui.theme.PocketTheme
import com.anutej.logseqpocket.ui.theme.noteBodyStyle

val IndentStep: Dp = 18.dp
private val BulletWidth = 26.dp
private val StartPad = 12.dp

/**
 * One outline row. Indentation guides are drawn for each ancestor level; the bullet toggles
 * children. Text is selectable/copyable; references inside it are tappable.
 */
@Composable
fun BlockRow(
    row: BlockRowUi,
    ctx: RenderContext,
    onToggle: () -> Unit,
    onTagClick: (String) -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    depthOffset: Int = 0,
) {
    val colors = PocketTheme.colors
    val depth = (row.block.depth - depthOffset).coerceAtLeast(0)
    val guide = colors.guideLine
    val density = LocalDensity.current
    val bg by animateColorAsState(
        if (highlighted) colors.highlight else Color.Transparent,
        animationSpec = tween(durationMillis = 600),
        label = "focus",
    )
    val bodyStyle = noteBodyStyle(PocketTheme.readingSize)
    val lineHeight = with(density) { bodyStyle.lineHeight.toDp() }
    val isEmpty = row.block.content.isBlank() && row.tags.isEmpty()

    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .drawBehind {
                val stroke = 1.dp.toPx()
                for (level in 0 until depth) {
                    val x = (StartPad + IndentStep * level + BulletWidth / 2).toPx()
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), stroke)
                }
            }
            .padding(start = StartPad + IndentStep * depth, end = 16.dp),
    ) {
        Bullet(
            hasChildren = row.hasChildren,
            expanded = row.expanded,
            faint = isEmpty,
            height = if (isEmpty) 18.dp else lineHeight + 6.dp,
            onToggle = onToggle,
        )
        Column(Modifier.weight(1f).padding(top = 3.dp, bottom = if (isEmpty) 0.dp else 5.dp)) {
            if (!isEmpty) {
                if (row.md.isNotEmpty()) {
                    SelectionContainer { NoteContent(row.md, ctx, style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurface)) }
                }
                if (row.tags.isNotEmpty()) {
                    TagRow(
                        row.tags.map { t -> t.name to { onTagClick(t.tagKey) } },
                        Modifier.padding(top = if (row.md.isEmpty()) 0.dp else 6.dp),
                    )
                }
                if (row.props.isNotEmpty()) PropertyRow(row.props, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Bullet(hasChildren: Boolean, expanded: Boolean, faint: Boolean, height: Dp, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(BulletWidth)
            .height(height)
            .then(
                if (hasChildren) Modifier
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggle)
                    .semantics {
                        contentDescription = "Toggle nested blocks"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasChildren && !expanded -> Box(Modifier.size(16.dp).background(ring, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).background(primary, CircleShape))
            }
            hasChildren -> Box(Modifier.size(7.dp).background(primary, CircleShape))
            else -> Box(Modifier.size(5.dp).background(muted.copy(alpha = if (faint) 0.25f else 0.6f), CircleShape))
        }
    }
}
