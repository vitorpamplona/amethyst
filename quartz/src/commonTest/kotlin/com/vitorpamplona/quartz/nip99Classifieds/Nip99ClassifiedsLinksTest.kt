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
package com.vitorpamplona.quartz.nip99Classifieds

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip99ClassifiedsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun classifiedsCitationsAreMentions() {
        val cited = "2".repeat(64)
        val listing = "30402:$author:bike"
        val person = "a".repeat(64)
        val npub = Hex.decode("b".repeat(64)).toNpub()
        val event =
            ClassifiedsEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("d", "car"), arrayOf("e", cited), arrayOf("a", listing), arrayOf("p", person), arrayOf("t", "Cars")),
                "ask nostr:$npub",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.MENTION, LinkTarget.Event(cited), "e"),
                Link(Relation.MENTION, LinkTarget.Address(listing), "a"),
                Link(Relation.MENTION, LinkTarget.User(person), "p"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "cars"), "t"),
                Link(Relation.MENTION, LinkTarget.User("b".repeat(64)), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }
}
