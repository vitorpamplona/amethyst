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
package com.vitorpamplona.quartz.nip32Labeling

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip32LabelingLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun everyTargetIsLabeledAndCarriesTheLabels() {
        val note = "e1".repeat(32)
        val person = "b1".repeat(32)
        val event =
            LabelEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("L", "ISO-639-1"),
                    arrayOf("l", "en", "ISO-639-1"),
                    arrayOf("l", "nsfw"),
                    arrayOf("e", note, "wss://relay.example/"),
                    arrayOf("p", person),
                    arrayOf("a", "30023:$person:post"),
                    // on a label event t and r are what is labeled, not its own topics
                    arrayOf("t", "Bitcoin"),
                    arrayOf("r", "wss://relay.example/"),
                ),
                "",
                sig,
            )
        val labels = mapOf("labels" to listOf("ISO-639-1:en", "ugc:nsfw"))

        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("L", "ISO-639-1"), "L"),
                Link(Relation.TAG, LinkTarget.Tag("l", "en"), "l"),
                Link(Relation.TAG, LinkTarget.Tag("l", "nsfw"), "l"),
                Link(Relation.LABELED, LinkTarget.Event(note), "e", labels),
                Link(Relation.LABELED, LinkTarget.User(person), "p", labels),
                Link(Relation.LABELED, LinkTarget.Address("30023:$person:post"), "a", labels),
                Link(Relation.LABELED, LinkTarget.Tag("t", "bitcoin"), "t", labels),
                Link(Relation.LABELED, LinkTarget.Tag("r", "wss://relay.example/"), "r", labels),
            ),
            event.links(),
        )
    }

    @Test
    fun aLabelWithNoTargetLabelsOnlyItself() {
        val event = LabelEvent(id, me, 1L, arrayOf(arrayOf("L", "#t"), arrayOf("l", "nostr", "#t")), "", sig)
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("L", "#t"), "L"),
                Link(Relation.TAG, LinkTarget.Tag("l", "nostr"), "l"),
            ),
            event.links(),
        )
    }
}
