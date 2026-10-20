package com.anutej.logseqpocket.ui.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.anutej.logseqpocket.data.db.BlockEntity
import com.anutej.logseqpocket.domain.RefIndex
import com.anutej.logseqpocket.domain.RefTarget
import com.anutej.logseqpocket.domain.markdown.Inline
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.MdBlock
import com.anutej.logseqpocket.domain.markdown.isUuid
import com.anutej.logseqpocket.domain.markdown.plainText
import com.anutej.logseqpocket.ui.theme.CodeFontFamily
import com.anutej.logseqpocket.ui.theme.PocketColors
import com.anutej.logseqpocket.ui.theme.PocketTheme
import kotlinx.coroutines.launch

/** Navigation requested by tapping something inside note text. */
sealed interface NavRequest {
    data class Page(val uuid: String, val focusBlock: String? = null) : NavRequest
    data class Tag(val tagKey: String) : NavRequest
    data class External(val url: String) : NavRequest
    data class Missing(val label: String) : NavRequest
}

@Immutable
class RenderContext(
    val refs: RefIndex,
    val blockRefs: Map<String, BlockEntity> = emptyMap(),
    val onNavigate: (NavRequest) -> Unit = {},
    /** False on the front of a cloze flashcard. */
    val revealCloze: Boolean = true,
    /** Search term to highlight, lowercase. */
    val highlight: String? = null,
)

private fun safeExternal(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

fun buildNoteAnnotated(inlines: List<Inline>, ctx: RenderContext, colors: PocketColors): AnnotatedString =
    buildAnnotatedString {
        fun link(req: NavRequest, style: SpanStyle, label: String) {
            withLink(
                LinkAnnotation.Clickable(
                    tag = label,
                    styles = TextLinkStyles(style = style),
                    linkInteractionListener = { ctx.onNavigate(req) },
                )
            ) { append(label) }
        }

        val linkStyle = SpanStyle(color = colors.linkText, fontWeight = FontWeight.Medium)
        val missingStyle = SpanStyle(color = colors.missingLink, textDecoration = TextDecoration.Underline)
        val tagStyle = SpanStyle(color = colors.tagChipText, background = colors.tagChip, fontWeight = FontWeight.Medium)
        val codeStyle = SpanStyle(fontFamily = CodeFontFamily, background = colors.codeBackground, color = colors.codeText, fontSize = 0.9.em)

        fun target(t: RefTarget, shownAs: String, style: SpanStyle) = when (t) {
            is RefTarget.Page -> link(NavRequest.Page(t.uuid), style, shownAs)
            is RefTarget.Tag -> link(NavRequest.Tag(t.tagKey), style, shownAs)
            is RefTarget.Missing -> link(NavRequest.Missing(t.label), missingStyle, shownAs)
        }

        fun walk(list: List<Inline>) {
            for (n in list) when (n) {
                is Inline.Text -> appendHighlighted(n.text, ctx.highlight, colors)
                is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { walk(n.children) }
                is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(n.children) }
                is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { walk(n.children) }
                is Inline.Highlight -> withStyle(SpanStyle(background = colors.highlight)) { walk(n.children) }
                is Inline.Code -> withStyle(codeStyle) { append(' '); append(n.text); append(' ') }
                is Inline.Math -> withStyle(SpanStyle(fontFamily = CodeFontFamily, fontStyle = FontStyle.Italic, color = colors.codeText)) {
                    append(n.text)
                }
                is Inline.PageRef -> {
                    val t = ctx.refs.page(n.target)
                    // Show the original text for name refs; resolve UUID refs to the page title.
                    val shown = if (t is RefTarget.Missing || !isUuid(n.target)) n.target else t.label
                    target(t, shown, linkStyle)
                }
                is Inline.TagRef -> {
                    val t = ctx.refs.tag(n.target)
                    val name = if (isUuid(n.target)) t.label else n.target
                    target(t, " #$name ", if (t is RefTarget.Missing) missingStyle else tagStyle)
                }
                is Inline.BlockRef -> {
                    val b = ctx.blockRefs[n.uuid]
                    if (b == null) {
                        link(NavRequest.Missing("Referenced block"), missingStyle, "((block not in export))")
                    } else {
                        val text = plainText(InlineParser.parse(b.content)).replace('\n', ' ').let {
                            if (it.length > 140) it.take(140) + "…" else it
                        }
                        link(NavRequest.Page(b.pageUuid, b.uuid), linkStyle.copy(textDecoration = TextDecoration.Underline), text)
                    }
                }
                is Inline.Link -> {
                    val label = plainText(n.label).ifBlank { n.url }.let { if (n.image) "[image] $it" else it }
                    if (safeExternal(n.url)) link(NavRequest.External(n.url), linkStyle.copy(textDecoration = TextDecoration.Underline), label)
                    else { append(label); withStyle(SpanStyle(color = colors.missingLink)) { append(" (${n.url})") } }
                }
                is Inline.Url -> link(NavRequest.External(n.url), linkStyle.copy(textDecoration = TextDecoration.Underline), n.url)
                is Inline.Cloze -> if (ctx.revealCloze) {
                    withStyle(SpanStyle(background = colors.highlight, fontWeight = FontWeight.SemiBold)) { append(n.text) }
                } else {
                    withStyle(SpanStyle(background = colors.codeBackground, color = colors.codeBackground)) {
                        append(" [ ? ] ")
                    }
                }
                is Inline.Macro -> withStyle(codeStyle.copy(color = colors.missingLink)) { append(n.raw) }
            }
        }
        walk(inlines)
    }

private fun AnnotatedString.Builder.appendHighlighted(text: String, term: String?, colors: PocketColors) {
    if (term.isNullOrEmpty()) { append(text); return }
    val lower = text.lowercase()
    var from = 0
    while (true) {
        val i = lower.indexOf(term, from)
        if (i < 0) { append(text.substring(from)); return }
        append(text.substring(from, i))
        withStyle(SpanStyle(background = colors.highlight, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(i, i + term.length))
        }
        from = i + term.length
    }
}

/** Renders one Logseq block's content (already parsed) with Markdown and Logseq references. */
@Composable
fun NoteContent(
    blocks: List<MdBlock>,
    ctx: RenderContext,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = PocketTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) when (b) {
            is MdBlock.Paragraph -> Text(remember(b, ctx, colors) { buildNoteAnnotated(b.inlines, ctx, colors) }, style = style)
            is MdBlock.Heading -> Text(
                remember(b, ctx, colors) { buildNoteAnnotated(b.inlines, ctx, colors) },
                style = when (b.level) {
                    1 -> MaterialTheme.typography.headlineSmall
                    2 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                },
            )
            is MdBlock.CodeBlock -> CodeBlockView(b.language, b.code)
            is MdBlock.Quote -> Row {
                Box(Modifier.width(3.dp).height(style.lineHeight.value.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                Spacer(Modifier.width(10.dp))
                Text(
                    remember(b, ctx, colors) { buildNoteAnnotated(b.inlines, ctx, colors) },
                    style = style.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is MdBlock.ListItem -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                val marker = if (b.marker.first().isDigit()) b.marker else "•"
                Text(marker, style = style, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 18.dp))
                Text(remember(b, ctx, colors) { buildNoteAnnotated(b.inlines, ctx, colors) }, style = style)
            }
            MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun CodeBlockView(language: String?, code: String, modifier: Modifier = Modifier) {
    val colors = PocketTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(modifier.fillMaxWidth(), color = colors.codeBackground, shape = MaterialTheme.shapes.small) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", code))) } },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Outlined.ContentCopy, "Copy code", Modifier.size(18.dp)) }
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = CodeFontFamily, lineHeight = 1.45.em),
                    color = colors.codeText,
                    softWrap = false,
                )
            }
        }
    }
}
