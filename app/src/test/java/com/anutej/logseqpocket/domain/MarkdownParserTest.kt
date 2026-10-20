package com.anutej.logseqpocket.domain

import com.anutej.logseqpocket.domain.markdown.Inline
import com.anutej.logseqpocket.domain.markdown.InlineParser
import com.anutej.logseqpocket.domain.markdown.MarkdownParser
import com.anutej.logseqpocket.domain.markdown.MdBlock
import com.anutej.logseqpocket.domain.markdown.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    private fun p(s: String) = InlineParser.parse(s)

    @Test fun boldContainingInlineCode() {
        val r = p("**The Shift (`<<`)**: `int mask = 1 << num;` creates")
        val bold = r[0] as Inline.Bold
        assertEquals(listOf(Inline.Text("The Shift ("), Inline.Code("<<"), Inline.Text(")")), bold.children)
        assertEquals(Inline.Code("int mask = 1 << num;"), r[2])
    }

    @Test fun underscoresInsideIdentifiersAreNotItalic() {
        val r = p("calculate target - current_value = complement")
        assertEquals(listOf(Inline.Text("calculate target - current_value = complement")), r)
    }

    @Test fun italicAndStrikeAndHighlight() {
        val r = p("*Grouping:* ~~old~~ ==hot== ^^also^^")
        assertTrue(r[0] is Inline.Italic)
        assertTrue(r.any { it is Inline.Strike })
        assertEquals(2, r.count { it is Inline.Highlight })
    }

    @Test fun logseqReferences() {
        val uuid = "6aaec60d-7a2e-488a-9e45-e9e21dae53f0"
        val r = p("#[[$uuid]] leetcode and [[Some Page]] plus #algo-pattern, ((${uuid}))")
        assertEquals(Inline.TagRef(uuid, bracketed = true), r[0])
        assertTrue(r.contains(Inline.PageRef("Some Page")))
        assertTrue(r.contains(Inline.TagRef("algo-pattern", bracketed = false)))
        assertTrue(r.contains(Inline.BlockRef(uuid)))
    }

    @Test fun hashInsideWordIsNotATag() {
        assertEquals(listOf(Inline.Text("C# and F#")), p("C# and F#"))
    }

    @Test fun urlsTrimTrailingPunctuation() {
        val r = p("see https://example.com/docs. and (https://a.b/c_(d))")
        assertTrue(r.contains(Inline.Url("https://example.com/docs")))
        assertTrue(r.contains(Inline.Url("https://a.b/c_(d)")))
    }

    @Test fun markdownLinkAndUnsafeSchemeKeptAsLink() {
        val r = p("[docs](https://x.y) [bad](javascript:alert)")
        assertEquals(Inline.Link(listOf(Inline.Text("docs")), "https://x.y", image = false), r[0])
        assertEquals("javascript:alert", (r[2] as Inline.Link).url) // renderer refuses to open it
    }

    @Test fun mathAndDollarAmounts() {
        assertEquals(Inline.Math("O(N^2)", display = false), p("takes \$O(N^2)\$ time")[1])
        assertEquals(listOf(Inline.Text("costs \$5 and \$10")), p("costs \$5 and \$10"))
    }

    @Test fun clozeAndOtherMacros() {
        val r = p("The capital is {{cloze Paris}} {{embed [[x]]}}")
        assertEquals(Inline.Cloze("Paris"), r[1])
        assertEquals(Inline.Macro("{{embed [[x]]}}"), r[3])
    }

    @Test fun htmlIsLiteralText() {
        val s = "<script>alert(1)</script> stays literal"
        assertEquals(s, plainText(p(s)))
    }

    @Test fun plainTextPreservesEverythingButMarkup() {
        val s = "dp[i] = dp[val + dp[i + 1]] and also 2 digit"
        assertEquals(s, plainText(p(s)))
    }

    @Test fun fencedCodeBlockKeepsExactText() {
        val code = "int x = 1;\n    if (x < 2) { return; }"
        val r = MarkdownParser.parse("Intro\n```java\n$code\n```\nAfter")
        assertEquals(MdBlock.CodeBlock("java", code), r[1])
        assertEquals(3, r.size)
    }

    @Test fun unterminatedFenceStillShowsCode() {
        val r = MarkdownParser.parse("```\nline1\nline2")
        assertEquals(MdBlock.CodeBlock(null, "line1\nline2"), r.single())
    }

    @Test fun headingVersusTag() {
        val r = MarkdownParser.parse("## Title\n#tag not heading")
        assertTrue(r[0] is MdBlock.Heading)
        assertTrue(r[1] is MdBlock.Paragraph)
    }

    @Test fun listsAndMultilineParagraphs() {
        val r = MarkdownParser.parse("line one\nline two\n\n- item\n  - nested\n1. first")
        assertEquals("line one\nline two", plainText((r[0] as MdBlock.Paragraph).inlines))
        assertEquals(0, (r[1] as MdBlock.ListItem).indent)
        assertEquals(1, (r[2] as MdBlock.ListItem).indent)
        assertEquals("1.", (r[3] as MdBlock.ListItem).marker)
    }
}
