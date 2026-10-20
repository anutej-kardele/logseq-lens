package com.anutej.logseqpocket.domain.markdown

/**
 * A deliberately small Markdown + Logseq syntax model.
 *
 * Nothing here interprets HTML or executes anything: unknown syntax (including raw HTML) is kept
 * as literal [Inline.Text], so note text is never dropped.
 */
sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val children: List<Inline>) : Inline
    data class Italic(val children: List<Inline>) : Inline
    data class Strike(val children: List<Inline>) : Inline
    data class Highlight(val children: List<Inline>) : Inline
    data class Code(val text: String) : Inline
    data class Math(val text: String, val display: Boolean) : Inline
    /** `[[target]]` — target is a page name or a UUID. */
    data class PageRef(val target: String) : Inline
    /** `#tag` or `#[[target]]`. */
    data class TagRef(val target: String, val bracketed: Boolean) : Inline
    /** `((uuid))` */
    data class BlockRef(val uuid: String) : Inline
    data class Link(val label: List<Inline>, val url: String, val image: Boolean) : Inline
    data class Url(val url: String) : Inline
    /** `{{cloze text}}` */
    data class Cloze(val text: String) : Inline
    /** Any other `{{macro …}}`, shown literally. */
    data class Macro(val raw: String) : Inline
}

sealed interface MdBlock {
    data class Paragraph(val inlines: List<Inline>) : MdBlock
    data class Heading(val level: Int, val inlines: List<Inline>) : MdBlock
    data class CodeBlock(val language: String?, val code: String) : MdBlock
    data class Quote(val inlines: List<Inline>) : MdBlock
    data class ListItem(val indent: Int, val marker: String, val inlines: List<Inline>) : MdBlock
    data object Rule : MdBlock
}

private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isUuid(s: String): Boolean = UUID_RE.matches(s)

object MarkdownParser {
    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val quoteRe = Regex("^>\\s?(.*)$")
    private val listRe = Regex("^(\\s*)([-*+]|\\d{1,3}[.)])\\s+(.*)$")
    private val ruleRe = Regex("^\\s*(---+|\\*\\*\\*+)\\s*$")

    fun parse(content: String): List<MdBlock> {
        if (content.isEmpty()) return emptyList()
        val lines = content.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()

        fun flushPara() {
            if (para.isNotEmpty()) {
                out += MdBlock.Paragraph(InlineParser.parse(para.joinToString("\n")))
                para.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") -> {
                    flushPara()
                    val afterFence = trimmed.removePrefix("```")
                    val inlineClose = afterFence.indexOf("```")
                    if (inlineClose >= 0) {
                        // Single-line fence: ```code```
                        out += MdBlock.CodeBlock(null, afterFence.substring(0, inlineClose))
                        i++
                        continue
                    }
                    val lang = afterFence.trim().ifEmpty { null }
                    val code = StringBuilder()
                    i++
                    while (i < lines.size) {
                        if (lines[i].trimStart().startsWith("```")) { i++; break }
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    out += MdBlock.CodeBlock(lang, code.toString())
                    continue
                }
                line.isBlank() -> flushPara()
                ruleRe.matches(line) -> { flushPara(); out += MdBlock.Rule }
                headingRe.matches(line) -> {
                    flushPara()
                    val m = headingRe.find(line)!!
                    out += MdBlock.Heading(m.groupValues[1].length, InlineParser.parse(m.groupValues[2]))
                }
                quoteRe.matches(line) -> {
                    flushPara()
                    out += MdBlock.Quote(InlineParser.parse(quoteRe.find(line)!!.groupValues[1]))
                }
                listRe.matches(line) -> {
                    flushPara()
                    val m = listRe.find(line)!!
                    out += MdBlock.ListItem(m.groupValues[1].length / 2, m.groupValues[2], InlineParser.parse(m.groupValues[3]))
                }
                else -> para += line
            }
            i++
        }
        flushPara()
        return out
    }
}

object InlineParser {
    fun parse(s: String): List<Inline> = Scanner(s).run()

    private const val TAG_STOP = ",.!?;:\"'()[]{}<>`"

    private class Scanner(private val s: String) {
        private val out = mutableListOf<Inline>()
        private val buf = StringBuilder()

        fun run(): List<Inline> {
            var i = 0
            while (i < s.length) {
                val hit = matchAt(i)
                if (hit != null) {
                    flush()
                    out += hit.first
                    i = hit.second
                } else {
                    buf.append(s[i])
                    i++
                }
            }
            flush()
            return out
        }

        private fun flush() {
            if (buf.isNotEmpty()) {
                out += Inline.Text(buf.toString())
                buf.clear()
            }
        }

        private fun prevIsBoundary(i: Int): Boolean = i == 0 || s[i - 1].isWhitespace() || s[i - 1] in "([{\"'"
        private fun isWordChar(c: Char) = c.isLetterOrDigit()

        private fun matchAt(i: Int): Pair<Inline, Int>? = when (s[i]) {
            '`' -> codeSpan(i)
            '\\' -> escape(i)
            '$' -> math(i)
            '[' -> if (s.startsWith("[[", i)) pageRef(i) else link(i, image = false)
            '!' -> if (s.startsWith("![", i)) link(i + 1, image = true) else null
            '#' -> tag(i)
            '(' -> blockRef(i)
            '{' -> macro(i)
            'h' -> url(i)
            '*' -> if (s.startsWith("**", i)) delimited(i, "**", { Inline.Bold(it) }) else single(i, '*')
            '_' -> if (s.startsWith("__", i)) delimited(i, "__", { Inline.Bold(it) }, wordBoundary = true) else single(i, '_')
            '~' -> if (s.startsWith("~~", i)) delimited(i, "~~", { Inline.Strike(it) }) else null
            '=' -> if (s.startsWith("==", i)) delimited(i, "==", { Inline.Highlight(it) }) else null
            '^' -> if (s.startsWith("^^", i)) delimited(i, "^^", { Inline.Highlight(it) }) else null
            else -> null
        }

        private fun codeSpan(i: Int): Pair<Inline, Int>? {
            var n = 0
            while (i + n < s.length && s[i + n] == '`') n++
            val fence = "`".repeat(n)
            val close = s.indexOf(fence, i + n)
            if (close < 0 || close == i + n) return null
            var text = s.substring(i + n, close)
            if (text.length >= 2 && text.startsWith(' ') && text.endsWith(' ') && text.isNotBlank()) {
                text = text.substring(1, text.length - 1)
            }
            return Inline.Code(text) to close + n
        }

        private fun escape(i: Int): Pair<Inline, Int>? {
            if (i + 1 >= s.length) return null
            val c = s[i + 1]
            return if (c in "\\`*_{}[]()#+-.!~=^\$|>") Inline.Text(c.toString()) to i + 2 else null
        }

        private fun math(i: Int): Pair<Inline, Int>? {
            if (s.startsWith("$$", i)) {
                val close = s.indexOf("$$", i + 2)
                if (close <= i + 2) return null
                return Inline.Math(s.substring(i + 2, close), display = true) to close + 2
            }
            if (i + 1 >= s.length || s[i + 1].isWhitespace() || s[i + 1] == '$') return null
            var j = i + 1
            while (j < s.length && s[j] != '$' && s[j] != '\n') j++
            if (j >= s.length || s[j] != '$') return null
            if (s[j - 1].isWhitespace()) return null
            if (j + 1 < s.length && s[j + 1].isDigit()) return null
            return Inline.Math(s.substring(i + 1, j), display = false) to j + 1
        }

        /** Returns the index of the `]]` closing the `[[` at [open], honouring nesting. */
        private fun closeBrackets(open: Int): Int {
            var depth = 0
            var k = open
            while (k < s.length - 1) {
                when {
                    s.startsWith("[[", k) -> { depth++; k += 2 }
                    s.startsWith("]]", k) -> { depth--; if (depth == 0) return k; k += 2 }
                    s[k] == '\n' -> return -1
                    else -> k++
                }
            }
            return -1
        }

        private fun pageRef(i: Int): Pair<Inline, Int>? {
            val close = closeBrackets(i)
            if (close < 0) return null
            val target = s.substring(i + 2, close)
            if (target.isBlank()) return null
            return Inline.PageRef(target) to close + 2
        }

        private fun link(i: Int, image: Boolean): Pair<Inline, Int>? {
            val labelEnd = s.indexOf(']', i + 1)
            if (labelEnd < 0 || labelEnd + 1 >= s.length || s[labelEnd + 1] != '(') return null
            val urlEnd = s.indexOf(')', labelEnd + 2)
            if (urlEnd < 0) return null
            val url = s.substring(labelEnd + 2, urlEnd).trim()
            if (url.isEmpty() || url.contains(' ') || url.contains('\n')) return null
            val label = s.substring(i + 1, labelEnd)
            if (label.contains('\n')) return null
            return Inline.Link(parse(label), url, image) to urlEnd + 1
        }

        private fun tag(i: Int): Pair<Inline, Int>? {
            if (!prevIsBoundary(i)) return null
            if (s.startsWith("#[[", i)) {
                val close = closeBrackets(i + 1)
                if (close < 0) return null
                val target = s.substring(i + 3, close)
                if (target.isBlank()) return null
                return Inline.TagRef(target, bracketed = true) to close + 2
            }
            var j = i + 1
            while (j < s.length && !s[j].isWhitespace() && s[j] !in TAG_STOP && s[j] != '#') j++
            // Allow internal punctuation like "algo-pattern" but trim trailing stops naturally.
            if (j == i + 1) return null
            return Inline.TagRef(s.substring(i + 1, j), bracketed = false) to j
        }

        private fun blockRef(i: Int): Pair<Inline, Int>? {
            if (!s.startsWith("((", i)) return null
            val close = s.indexOf("))", i + 2)
            if (close < 0) return null
            val inner = s.substring(i + 2, close).trim()
            if (!isUuid(inner)) return null
            return Inline.BlockRef(inner.lowercase()) to close + 2
        }

        private fun macro(i: Int): Pair<Inline, Int>? {
            if (!s.startsWith("{{", i)) return null
            val close = s.indexOf("}}", i + 2)
            if (close < 0) return null
            val inner = s.substring(i + 2, close).trim()
            val raw = s.substring(i, close + 2)
            if (inner.startsWith("cloze ", ignoreCase = true) || inner.equals("cloze", ignoreCase = true)) {
                return Inline.Cloze(inner.substringAfter(' ', "").trim()) to close + 2
            }
            return Inline.Macro(raw) to close + 2
        }

        private fun url(i: Int): Pair<Inline, Int>? {
            val scheme = when {
                s.startsWith("https://", i) -> 8
                s.startsWith("http://", i) -> 7
                else -> return null
            }
            if (!prevIsBoundary(i) && s[i - 1] != '<') return null
            var j = i
            while (j < s.length && !s[j].isWhitespace() && s[j] != '>') j++
            // Trim trailing punctuation, keeping a ')' that closes a '(' inside the URL.
            while (j > i + scheme) {
                val c = s[j - 1]
                if (c in ".,;:!?'\"") { j--; continue }
                if (c == ')' && s.substring(i, j).count { it == '(' } < s.substring(i, j).count { it == ')' }) { j--; continue }
                break
            }
            if (j <= i + scheme) return null
            return Inline.Url(s.substring(i, j)) to j
        }

        private fun delimited(
            i: Int,
            delim: String,
            make: (List<Inline>) -> Inline,
            wordBoundary: Boolean = false,
        ): Pair<Inline, Int>? {
            val start = i + delim.length
            if (start >= s.length || s[start].isWhitespace()) return null
            if (wordBoundary && i > 0 && isWordChar(s[i - 1])) return null
            var close = s.indexOf(delim, start)
            while (close >= 0) {
                val okLeft = !s[close - 1].isWhitespace()
                val okRight = !wordBoundary || close + delim.length >= s.length || !isWordChar(s[close + delim.length])
                if (close > start && okLeft && okRight) break
                close = s.indexOf(delim, close + 1)
            }
            if (close < 0) return null
            return make(parse(s.substring(start, close))) to close + delim.length
        }

        private fun single(i: Int, c: Char): Pair<Inline, Int>? {
            val start = i + 1
            if (start >= s.length || s[start].isWhitespace() || s[start] == c) return null
            if (c == '_' && i > 0 && isWordChar(s[i - 1])) return null
            var j = start
            while (j < s.length) {
                if (s[j] == '\n') return null
                if (s[j] == c && !s[j - 1].isWhitespace() && j > start &&
                    (j + 1 >= s.length || s[j + 1] != c) &&
                    (c != '_' || j + 1 >= s.length || !isWordChar(s[j + 1]))
                ) {
                    return Inline.Italic(parse(s.substring(start, j))) to j + 1
                }
                if (s[j] == '`') {
                    // Don't let emphasis close inside a code span.
                    val close = s.indexOf('`', j + 1)
                    if (close > 0) { j = close + 1; continue }
                }
                j++
            }
            return null
        }
    }
}

/** Every reference found in a parsed block, including those nested in emphasis or link labels. */
fun collectRefs(blocks: List<MdBlock>): List<Inline> {
    val out = mutableListOf<Inline>()
    fun walk(list: List<Inline>) {
        for (n in list) when (n) {
            is Inline.PageRef, is Inline.TagRef, is Inline.BlockRef -> out += n
            is Inline.Bold -> walk(n.children)
            is Inline.Italic -> walk(n.children)
            is Inline.Strike -> walk(n.children)
            is Inline.Highlight -> walk(n.children)
            is Inline.Link -> walk(n.label)
            else -> Unit
        }
    }
    for (b in blocks) when (b) {
        is MdBlock.Paragraph -> walk(b.inlines)
        is MdBlock.Heading -> walk(b.inlines)
        is MdBlock.Quote -> walk(b.inlines)
        is MdBlock.ListItem -> walk(b.inlines)
        else -> Unit
    }
    return out
}

/** Plain text of inline nodes with markup removed (used for labels, previews and card titles). */
fun plainText(list: List<Inline>, resolve: (Inline) -> String? = { null }): String = buildString {
    for (n in list) {
        resolve(n)?.let { append(it); continue }
        when (n) {
            is Inline.Text -> append(n.text)
            is Inline.Bold -> append(plainText(n.children, resolve))
            is Inline.Italic -> append(plainText(n.children, resolve))
            is Inline.Strike -> append(plainText(n.children, resolve))
            is Inline.Highlight -> append(plainText(n.children, resolve))
            is Inline.Code -> append(n.text)
            is Inline.Math -> append(n.text)
            is Inline.PageRef -> append(n.target)
            is Inline.TagRef -> append('#').append(n.target)
            is Inline.BlockRef -> append("((").append(n.uuid).append("))")
            is Inline.Link -> append(plainText(n.label, resolve))
            is Inline.Url -> append(n.url)
            is Inline.Cloze -> append(n.text)
            is Inline.Macro -> append(n.raw)
        }
    }
}
