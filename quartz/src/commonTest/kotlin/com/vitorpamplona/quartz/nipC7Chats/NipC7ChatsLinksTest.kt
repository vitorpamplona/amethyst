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
package com.vitorpamplona.quartz.nipC7Chats

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class NipC7ChatsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun theLastQuoteIsTheParent() {
        val quoted = "2".repeat(64)
        val parent = "3".repeat(64)
        val parentAuthor = "a".repeat(64)
        val mentioned = "b".repeat(64)
        val cited = "c".repeat(64)
        val npub = Hex.decode(cited).toNpub()
        val event =
            ChatEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("q", quoted),
                    arrayOf("q", parent, "wss://relay.example/", parentAuthor),
                    arrayOf("p", mentioned),
                    arrayOf("h", "group"),
                ),
                "yes nostr:$npub",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "q"),
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "q"),
                Link(Relation.MENTION, LinkTarget.User(mentioned), "p"),
                Link(Relation.GROUP, LinkTarget.Tag("h", "group"), "h"),
                Link(Relation.MENTION, LinkTarget.User(cited), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }
}
