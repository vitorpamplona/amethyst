/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.content.markdown

import com.vitorpamplona.amethyst.commons.content.DocumentDump
import com.vitorpamplona.amethyst.commons.content.Heading
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownToDocumentTest {
    private fun dump(markdown: String) = DocumentDump.dump(MarkdownToDocument().parse(markdown)).trimEnd()

    @Test
    fun inlineMarkup() {
        assertEquals(
            """
            H1["Title"]
            P["Some " em["em"] " and " strong["strong"] " and " del["del"] " and " code"code" " and " link(https://x.com "t")["link"] " and " img(https://x.com/a.png)"img" "." SB "soft line" BR SB "hard line" BR SB "end"]
            """.trimIndent(),
            dump("# Title\n\nSome *em* and **strong** and ~~del~~ and `code` and [link](https://x.com \"t\") and ![img](https://x.com/a.png).\nsoft line\\\nhard line  \nend"),
        )
    }

    @Test
    fun listsTasksAndLooseness() {
        assertEquals(
            """
            UL
              - 
                P["a"]
              - [ ] 
                P["task"]
              - [x] 
                P["done"]
            OL(3) loose
              - 
                P["one"]
              - 
                P["two"]
            """.trimIndent(),
            dump("- a\n- [ ] task\n- [x] done\n\n3. one\n\n4. two"),
        )
    }

    @Test
    fun nestedListsAndContinuations() {
        assertEquals(
            """
            OL(1) loose
              - 
                P["item one" SB "with continuation"]
                P["second paragraph"]
              - 
                P["item two"]
                UL
                  - 
                    P["nested a"]
                  - 
                    P["nested b"]
            """.trimIndent(),
            dump("1. item one\n   with continuation\n\n   second paragraph\n2. item two\n   - nested a\n   - nested b"),
        )
    }

    @Test
    fun blocks() {
        assertEquals(
            """
            QUOTE
              P["quote " strong["b"] SB "more"]
            HR
            CODE(kotlin)"val x = 1\n\nval y = 2"
            CODE()"indented\n  more"
            TABLE[L, R, C, N]
              | ["a"] | ["b"] | ["c"] | ["d"]
              | ["1"] | [strong["2"]] | ["3"] | ["4"]
            """.trimIndent(),
            dump("> quote **b**\n> more\n\n---\n\n```kotlin\nval x = 1\n\nval y = 2\n```\n\n    indented\n      more\n\n| a | b | c | d |\n|:--|--:|:-:|---|\n| 1 | **2** | 3 | 4 |"),
        )
    }

    /** The code blocks of [markdown], as the dump writes them, wherever they nest. */
    private fun codes(markdown: String) = dump(markdown).lines().map { it.trim() }.filter { it.startsWith("CODE") }

    @Test
    fun codeInsideQuotesAndListsLosesTheContainersPrefix() {
        assertEquals(listOf("CODE()\"code\\n  more\""), codes("> ```\n> code\n>   more\n> ```"))
        assertEquals(listOf("CODE()\"code\\nmore\""), codes(">     code\n>     more"))
        assertEquals(listOf("CODE()\"code\\n  line2\""), codes("- item\n\n  ```\n  code\n    line2\n  ```"))
        // CommonMark takes the opening fence's indentation off each line, and no more.
        assertEquals(listOf("CODE()\"a\\n b\\nc\""), codes("  ```\n  a\n   b\n c\n  ```"))
    }

    @Test
    fun links() {
        assertEquals(
            """
            P["Ref " link(https://example.com "T")["guide"] " and " link(https://s.com)["Short"] " and " link(https://auto.link)["https://auto.link"] " and [undefined][nope]"]
            """.trimIndent(),
            dump("Ref [guide][1] and [Short] and <https://auto.link> and [undefined][nope]\n\n[1]: https://example.com \"T\"\n[short]: https://s.com"),
        )
    }

    @Test
    fun linkedImages() {
        // A picture that links somewhere, with plain and with angle-bracketed destinations.
        assertEquals(
            """
            P[link(https://go.example.com/x)[img(https://example.com/a.png)"View"]]
            P[link(https://go.example.com/y)[img(https://example.com/a b.jpg)"Pic."]]
            """.trimIndent(),
            dump("[![View](https://example.com/a.png)](https://go.example.com/x)\n\n[![Pic\\.](<https://example.com/a b.jpg>)](<https://go.example.com/y>)"),
        )
    }

    @Test
    fun linkTextIsNotDetectedAgain() {
        // A link's own text is text: its URL, a hashtag or a mention in it are not links of their own.
        assertEquals(
            """
            P[link(https://dev.to/x)["https://dev.to/x"] " " link(https://a.com)["see " em["#tag"]]]
            """.trimIndent(),
            dump("[https://dev.to/x](https://dev.to/x) [see *#tag*](https://a.com)"),
        )
    }

    @Test
    fun emailAutolinks() {
        assertEquals(
            """
            P["write to " link(mailto:me@example.com)["me@example.com"] "."]
            """.trimIndent(),
            dump("write to <me@example.com>."),
        )
    }

    @Test
    fun windowsLineEndings() {
        assertEquals("P[\"one\"]\nP[\"two\" SB \"three\"]", dump("one\r\n\r\ntwo\r\nthree"))
    }

    @Test
    fun nostrTextIsFoundAsInNotes() {
        assertEquals(
            """
            P["See " nostr:BechSegment("nostr:npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m") " and " nostr:HashTagSegment("#tag") " at " nostr:LinkSegment("https://bare.link")]
            P[nostr:ImageSegment("https://example.com/photo.jpg")]
            """.trimIndent(),
            dump("See nostr:npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m and #tag at https://bare.link\n\nhttps://example.com/photo.jpg"),
        )
    }

    @Test
    fun fileNamesAreNotMedia() {
        // A file named in prose is not an image at a guessed host; a full URL to one is.
        assertEquals(
            """
            P["Open Scarlet_Witch.jpg or " nostr:ImageSegment("https://example.com/b.png")]
            """.trimIndent(),
            dump("Open Scarlet\\_Witch.jpg or https://example.com/b.png"),
        )
    }

    @Test
    fun mathFollowsTheNoteRules() {
        // $x^2$ is math; prices are not, though GFM's math syntax would take them.
        assertEquals(
            """
            P[nostr:MathSegment("${'$'}x^2${'$'}") " and costs ${'$'}5 and ${'$'}10"]
            """.trimIndent(),
            dump("\$x^2\$ and costs \$5 and \$10"),
        )
    }

    @Test
    fun htmlIsNotInterpreted() {
        // NIP-23: no HTML. Tags go, their text stays, and <br> breaks the line.
        assertEquals(
            """
            P["an " mark["mark"] " and html" BR "next"]
            """.trimIndent(),
            dump("an ==mark== and <b>html</b><br>next"),
        )
    }

    @Test
    fun alertsHeadingsEscapesAndEntities() {
        assertEquals(
            """
            H2["Setext"]
            CALLOUT(warning)
              P["alert body"]
            P["*escaped* & © 😀"]
            """.trimIndent(),
            dump("Setext\n------\n\n> [!WARNING]\n> alert body\n\n\\*escaped\\* &amp; &copy; &#x1F600;"),
        )
    }

    @Test
    fun headingsGetAnchors() {
        val doc = MarkdownToDocument().parse("## Getting Started!\n\ntext")
        val heading = doc.blocks.first() as Heading
        assertEquals("getting-started", heading.anchor)
    }

    @Test
    fun emptyAndBlank() {
        assertEquals("", dump(""))
        assertEquals("", dump("   \n\n  "))
    }
}
