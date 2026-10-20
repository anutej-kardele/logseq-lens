package com.anutej.logseqpocket.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anutej.logseqpocket.domain.PropChip
import com.anutej.logseqpocket.ui.theme.PocketTheme

@Composable
fun TagChip(name: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = PocketTheme.colors
    Surface(
        color = colors.tagChip,
        contentColor = colors.tagChipText,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Text(
            "#$name",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(names: List<Pair<String, (() -> Unit)?>>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { (n, click) -> TagChip(n, click) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyRow(props: List<PropChip>, modifier: Modifier = Modifier, onQueryClick: (() -> Unit)? = null) {
    if (props.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        props.forEach { p ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = if (p.kind == PropChip.Kind.Query && onQueryClick != null) Modifier.clickable(onClick = onQueryClick) else Modifier,
            ) {
                Text(
                    "${p.label}: ${p.value}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}
