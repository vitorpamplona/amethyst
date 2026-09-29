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
package com.vitorpamplona.quartz.experimental

import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentalNotesLinksTest {
    private val me = "0".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val note1 = "e1".repeat(32)
    private val note2 = "e2".repeat(32)
    private val note3 = "e3".repeat(32)
    private val article = "30023:$alice:post"

    private fun tags(vararg tags: Array<String>) = arrayOf(*tags)

    @Test
    fun zapPollReadsMarkedThreadsLikeKind1() {
        val tags =
            tags(
                arrayOf("e", note1, "", "root"),
                arrayOf("e", note2, "", "reply", alice),
                arrayOf("e", note3, "", "mention"),
                arrayOf("p", alice),
                arrayOf("p", bob),
                arrayOf("q", article),
                arrayOf("a", "30023:$bob:other"),
                arrayOf("poll_option", "0", "yes"),
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(note1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(note2), "e"),
                Link(Relation.MENTION, LinkTarget.Event(note3), "e"),
                // the parent's `e` names alice as its author
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(alice), "p"),
                Link(Relation.MENTION, LinkTarget.User(bob), "p"),
                Link(Relation.QUOTE, LinkTarget.Address(article), "q"),
                Link(Relation.MENTION, LinkTarget.Address("30023:$bob:other"), "a"),
            ),
            ZapPollEvent(me, me, 0, tags, "Which one?", me).links(),
        )
    }

    @Test
    fun zapPollReadsPositionalThreads() {
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(note1), "e"),
                Link(Relation.MENTION, LinkTarget.Event(note3), "e"),
                Link(Relation.PARENT, LinkTarget.Event(note2), "e"),
                // no author slot on the parent: every p is a mention
                Link(Relation.MENTION, LinkTarget.User(alice), "p"),
            ),
            ZapPollEvent(me, me, 0, tags(arrayOf("e", note1), arrayOf("e", note3), arrayOf("e", note2), arrayOf("p", alice)), "", me).links(),
        )
        // a lone root marker: the root is also the parent
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(note1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(note1), "e"),
            ),
            ZapPollEvent(me, me, 0, tags(arrayOf("e", note1, "", "root")), "", me).links(),
        )
    }

    @Test
    fun nipTextLinksForksQuotesMentionsAndKinds() {
        val forkedFrom = "30817:$alice:nip-01"
        val npub = Hex.decode(bob).toNpub()
        val tags =
            tags(
                arrayOf("d", "nip-01"),
                arrayOf("a", forkedFrom, "", "fork"),
                arrayOf("e", note1, "", "fork"),
                arrayOf("e", note2),
                arrayOf("a", article),
                arrayOf("q", note3),
                arrayOf("p", alice),
                arrayOf("k", "1"),
            )
        assertEquals(
            listOf(
                Link(Relation.FORK, LinkTarget.Address(forkedFrom), "a"),
                Link(Relation.FORK, LinkTarget.Event(note1), "e"),
                Link(Relation.MENTION, LinkTarget.Address(article), "a"),
                Link(Relation.QUOTE, LinkTarget.Event(note3), "q"),
                Link(Relation.MENTION, LinkTarget.User(alice), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
                Link(Relation.MENTION, LinkTarget.User(bob), Link.VIA_CONTENT),
            ),
            NipTextEvent(me, me, 0, tags, "# NIP-01\n\nThanks nostr:$npub", me).links(),
        )
    }

    @Test
    fun entityRatingEmitsOneLinkPerTargetWithItsScore() {
        val book = "30040:$alice:book"
        val tags =
            tags(
                arrayOf("d", "publication:$book"),
                arrayOf("m", "publication"),
                arrayOf("rating", "0.8"),
                arrayOf("a", book),
                arrayOf("A", book),
                arrayOf("e", note1),
                arrayOf("k", "30040"),
                arrayOf("p", alice),
            )
        val props = mapOf("mark" to "publication", "stars" to 4.0)
        assertEquals(
            listOf(
                Link(Relation.RATED, LinkTarget.Address(book), "a", props),
                Link(Relation.RATED, LinkTarget.Event(note1), "e", props),
                Link(Relation.TAG, LinkTarget.Tag("k", "30040"), "k"),
                Link(Relation.RATED_AUTHOR, LinkTarget.User(alice), "p", props),
            ),
            EntityRatingEvent(me, me, 0, tags, "Great read", me).links(),
        )
    }

    @Test
    fun entityRatingReadsASpecOnlyDByItsMark() {
        assertEquals(
            listOf(Link(Relation.RATED, LinkTarget.Tag("d", "hashtag:bitcoin"), "d", mapOf("mark" to "hashtag", "stars" to 5.0))),
            EntityRatingEvent(me, me, 0, tags(arrayOf("d", "hashtag:bitcoin"), arrayOf("m", "hashtag"), arrayOf("rating", "1")), "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.RATED, LinkTarget.User(bob), "d", mapOf("mark" to "profile"))),
            EntityRatingEvent(me, me, 0, tags(arrayOf("d", bob), arrayOf("m", "profile")), "", me).links(),
        )
        // no mark: a nostr event
        assertEquals(
            listOf(Link(Relation.RATED, LinkTarget.Event(note2), "d", mapOf("mark" to "event"))),
            EntityRatingEvent(me, me, 0, tags(arrayOf("d", note2)), "", me).links(),
        )
        // relays are not link targets
        assertEquals(
            emptyList<Link<*>>(),
            EntityRatingEvent(me, me, 0, tags(arrayOf("d", "relay:wss://relay.example/"), arrayOf("m", "relay")), "", me).links(),
        )
    }
}
