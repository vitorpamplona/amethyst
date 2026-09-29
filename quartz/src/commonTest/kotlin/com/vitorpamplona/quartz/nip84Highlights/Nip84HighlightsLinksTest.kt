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
package com.vitorpamplona.quartz.nip84Highlights

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.RoleProps
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip84HighlightsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val author = "b1".repeat(32)
    private val editor = "b2".repeat(32)
    private val mentioned = "b3".repeat(32)

    @Test
    fun aHighlightOfAnArticleNamesItsSourceAndItsAuthors() {
        val article = "30023:$author:article"
        val version = "e1".repeat(32)
        val event =
            HighlightEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("a", article, "wss://relay.example/"),
                    arrayOf("e", version),
                    arrayOf("p", author, "", "author"),
                    arrayOf("p", editor, "wss://relay.example/", "editor"),
                    arrayOf("p", "b4".repeat(32)),
                    arrayOf("p", mentioned, "", "mention"),
                    arrayOf("comment", "so true"),
                ),
                "the highlighted passage",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.HIGHLIGHTED, LinkTarget.Address(article), "a"),
                Link(Relation.HIGHLIGHTED, LinkTarget.Event(version), "e"),
                Link(Relation.HIGHLIGHTED_AUTHOR, LinkTarget.User(author), "p", RoleProps(listOf("author"))),
                Link(Relation.HIGHLIGHTED_AUTHOR, LinkTarget.User(editor), "p", RoleProps(listOf("editor"))),
                Link(Relation.HIGHLIGHTED_AUTHOR, LinkTarget.User("b4".repeat(32)), "p"),
                Link(Relation.MENTION, LinkTarget.User(mentioned), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun aHighlightOfTheWebNamesItsUrlAndItsExternalId() {
        val event =
            HighlightEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("r", "https://example.com/essay", "source"),
                    arrayOf("r", "https://example.com/cited", "mention"),
                    arrayOf("i", "isbn:9780765382030"),
                ),
                "a sentence",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.HIGHLIGHTED, LinkTarget.Tag("r", "https://example.com/essay"), "r"),
                Link(Relation.TAG, LinkTarget.Tag("r", "https://example.com/cited"), "r"),
                Link(Relation.HIGHLIGHTED, LinkTarget.Tag("i", "isbn:9780765382030"), "i"),
            ),
            event.links(),
        )
    }
}
