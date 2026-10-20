package com.anutej.logseqpocket.ui.scratchpad

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * One-line capture into the Scratchpad without opening the editor: type, choose text or checkbox,
 * press Send (or the keyboard's Done). The field clears so you can add the next item right away.
 */
@Composable
fun QuickAddField(
    onAdd: (text: String, checkbox: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialCheckbox: Boolean = false,
    placeholder: String = "Add to Scratchpad…",
    focusRequester: FocusRequester? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var checkbox by rememberSaveable { mutableStateOf(initialCheckbox) }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text, checkbox)
            text = ""
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(checked = checkbox, onCheckedChange = { checkbox = it }) {
            Icon(
                if (checkbox) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (checkbox) "Adding as checkbox item" else "Adding as text",
                tint = if (checkbox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                // Enter (soft or hardware) at the end adds the item; pasted multi-line text is kept as-is.
                if (v.endsWith("\n") && v.count { it == '\n' } > text.count { it == '\n' }) {
                    text = v.dropLast(1)
                    submit()
                } else {
                    text = v
                }
            },
            placeholder = { Text(if (checkbox) "New to-do…" else placeholder) },
            maxLines = 4,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Outlined.Send, "Add")
        }
    }
}
