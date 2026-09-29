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
package com.vitorpamplona.quartz.nip25Reactions

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip25ReactionsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val threadRoot = "e1".repeat(32)
    private val target = "e2".repeat(32)
    private val rootAuthor = "b1".repeat(32)
    private val targetAuthor = "b2".repeat(32)

    @Test
    fun theLastETagIsTheTargetAndTheLastPItsAuthor() {
        // NIP-25: a reaction may copy its target's thread tags; the target is the LAST e and its
        // author the LAST p, the earlier ones are only mentions.
        val event =
            ReactionEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", threadRoot),
                    arrayOf("p", rootAuthor),
                    arrayOf("e", target, "wss://relay.example/", targetAuthor),
                    arrayOf("p", targetAuthor),
                    arrayOf("k", "1"),
                    arrayOf("emoji", "soapbox", "https://img.example/soapbox.png"),
                ),
                ":soapbox:",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.REACTED, LinkTarget.Event(target), "e"),
                Link(Relation.REACTED_AUTHOR, LinkTarget.User(targetAuthor), "p"),
                Link(Relation.MENTION, LinkTarget.Event(threadRoot), "e"),
                Link(Relation.MENTION, LinkTarget.User(rootAuthor), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aReactionToAnAddressableNamesItsAddress() {
        val address = "30023:$targetAuthor:article"
        val event =
            ReactionEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", target),
                    arrayOf("a", address),
                    arrayOf("p", targetAuthor),
                ),
                "+",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.REACTED, LinkTarget.Event(target), "e"),
                Link(Relation.REACTED, LinkTarget.Address(address), "a"),
                Link(Relation.REACTED_AUTHOR, LinkTarget.User(targetAuthor), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun anExternalReactionReactsToEveryExternalId() {
        val event =
            ExternalReactionEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("k", "podcast:guid"),
                    arrayOf("i", "podcast:guid:c90e609a", "https://podcast.example/"),
                    arrayOf("k", "podcast:item:guid"),
                    arrayOf("i", "podcast:item:guid:d98d189b"),
                ),
                "+",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.REACTED, LinkTarget.Tag("i", "podcast:guid:c90e609a"), "i"),
                Link(Relation.REACTED, LinkTarget.Tag("i", "podcast:item:guid:d98d189b"), "i"),
                Link(Relation.TAG, LinkTarget.Tag("k", "podcast:guid"), "k"),
                Link(Relation.TAG, LinkTarget.Tag("k", "podcast:item:guid"), "k"),
            ),
            event.links(),
        )
    }
}
