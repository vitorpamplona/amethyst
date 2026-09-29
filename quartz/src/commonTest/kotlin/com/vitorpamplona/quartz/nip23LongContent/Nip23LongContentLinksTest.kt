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
package com.vitorpamplona.quartz.nip23LongContent

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip23LongContentLinksTest {
    @Test
    fun anArticleOnlyMentionsAndQuotesNeverRepliesTo() {
        val note = "e1".repeat(32)
        val quoted = "e2".repeat(32)
        val person = "b1".repeat(32)
        val other = "30023:$person:other"
        val naddr = NAddress.create(30023, person, "cited", null)
        val event =
            LongFormContentEvent(
                "0".repeat(64),
                "f".repeat(64),
                1L,
                arrayOf(
                    arrayOf("d", "my-article"),
                    arrayOf("title", "On links"),
                    // an article has no thread: a root-marked e is still only a mention
                    arrayOf("e", note, "", "root"),
                    arrayOf("a", other),
                    arrayOf("p", person),
                    arrayOf("q", quoted),
                    arrayOf("t", "Nostr"),
                ),
                "As said in nostr:$naddr",
                "0".repeat(128),
            )

        assertEquals(
            listOf(
                Link(Relation.MENTION, LinkTarget.Event(note), "e"),
                Link(Relation.MENTION, LinkTarget.Address(other), "a"),
                Link(Relation.MENTION, LinkTarget.User(person), "p"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "nostr"), "t"),
                Link(Relation.MENTION, LinkTarget.Address("30023:$person:cited"), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }
}
