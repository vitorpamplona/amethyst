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
package com.vitorpamplona.quartz.nip10Notes

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.LinkProps
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip10NotesLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val root = "e1".repeat(32)
    private val parent = "e2".repeat(32)
    private val other = "e3".repeat(32)
    private val quoted = "e4".repeat(32)
    private val forked = "e5".repeat(32)

    private val rootAuthor = "b1".repeat(32)
    private val parentAuthor = "b2".repeat(32)
    private val bystander = "b3".repeat(32)

    private val community = "34550:$bystander:nostr"
    private val article = "30023:$parentAuthor:article"

    private fun note(
        tags: TagArray,
        content: String = "",
    ) = TextNoteEvent(id, me, 1L, tags, content, sig)

    private fun <P : LinkProps> ev(
        relation: Relation<P>,
        id: String,
        via: String? = "e",
    ) = Link(relation, LinkTarget.Event(id), via)

    private fun <P : LinkProps> us(
        relation: Relation<P>,
        pubkey: String,
        via: String? = "p",
    ) = Link(relation, LinkTarget.User(pubkey), via)

    private fun <P : LinkProps> ad(
        relation: Relation<P>,
        address: String,
        via: String? = "a",
    ) = Link(relation, LinkTarget.Address(address), via)

    private fun <P : LinkProps> tg(
        relation: Relation<P>,
        name: String,
        value: String,
    ) = Link(relation, LinkTarget.Tag(name, value), name)

    @Test
    fun markedReplyNamesRootParentAndOnlyTheParentsAuthor() {
        val npub = Hex.decode(bystander).toNpub()
        val event =
            note(
                arrayOf(
                    arrayOf("e", root, "wss://relay.example/", "root", rootAuthor),
                    arrayOf("e", other, "", "mention"),
                    arrayOf("e", parent, "wss://relay.example/", "reply", parentAuthor),
                    arrayOf("p", rootAuthor),
                    arrayOf("p", parentAuthor),
                    arrayOf("q", quoted),
                    arrayOf("q", article),
                    arrayOf("a", community, "wss://relay.example/"),
                    arrayOf("a", "30311:$bystander:live"),
                    arrayOf("t", "Nostr"),
                    arrayOf("r", "https://example.com/"),
                    arrayOf("g", "u4pruy"),
                    arrayOf("client", "Amethyst"),
                ),
                "gm nostr:$npub",
            )

        assertEquals(
            listOf(
                ev(Relation.ROOT, root),
                ev(Relation.PARENT, parent),
                ev(Relation.MENTION, other),
                // the root's author is notified, but only the parent's author is PARENT_AUTHOR
                us(Relation.MENTION, rootAuthor),
                us(Relation.PARENT_AUTHOR, parentAuthor),
                ev(Relation.QUOTE, quoted, "q"),
                ad(Relation.QUOTE, article, "q"),
                ad(Relation.COMMUNITY, community),
                ad(Relation.MENTION, "30311:$bystander:live"),
                tg(Relation.HASHTAG, "t", "nostr"),
                tg(Relation.TAG, "r", "https://example.com/"),
                tg(Relation.TAG, "g", "u4pruy"),
                us(Relation.MENTION, bystander, Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun positionalTagsAreRootFirstParentLastAndMentionsBetween() {
        val event =
            note(
                arrayOf(
                    arrayOf("e", root),
                    arrayOf("e", other, "wss://relay.example/"),
                    arrayOf("e", parent),
                    // positional tags name no author, so no p can be told to be the parent's
                    arrayOf("p", parentAuthor),
                ),
            )

        assertEquals(
            listOf(
                ev(Relation.ROOT, root),
                ev(Relation.PARENT, parent),
                ev(Relation.MENTION, other),
                us(Relation.MENTION, parentAuthor),
            ),
            event.links(),
        )
    }

    @Test
    fun aSinglePositionalTagIsBothRootAndParent() {
        assertEquals(
            listOf(ev(Relation.ROOT, root), ev(Relation.PARENT, root)),
            note(arrayOf(arrayOf("e", root))).links(),
        )
    }

    @Test
    fun aLoneRootMarkerIsADirectReplyToTheRoot() {
        val event =
            note(
                arrayOf(
                    arrayOf("e", root, "", "root", rootAuthor),
                    arrayOf("p", rootAuthor),
                ),
            )
        assertEquals(
            listOf(
                ev(Relation.ROOT, root),
                ev(Relation.PARENT, root),
                us(Relation.PARENT_AUTHOR, rootAuthor),
            ),
            event.links(),
        )
    }

    @Test
    fun aLoneReplyMarkerIsBothRootAndParent() {
        assertEquals(
            listOf(ev(Relation.ROOT, parent), ev(Relation.PARENT, parent)),
            note(arrayOf(arrayOf("e", parent, "wss://relay.example/", "reply"))).links(),
        )
    }

    @Test
    fun aReplyToAnAddressTakesTheParentAuthorFromTheCoordinate() {
        val event =
            note(
                arrayOf(
                    arrayOf("a", article, "", "reply"),
                    arrayOf("p", parentAuthor),
                    arrayOf("p", bystander),
                ),
            )
        assertEquals(
            listOf(
                ad(Relation.ROOT, article),
                ad(Relation.PARENT, article),
                us(Relation.PARENT_AUTHOR, parentAuthor),
                us(Relation.MENTION, bystander),
            ),
            event.links(),
        )
    }

    @Test
    fun forksAreNeitherRootNorParent() {
        val event =
            note(
                arrayOf(
                    arrayOf("e", forked, "", "fork", rootAuthor),
                    arrayOf("a", article, "", "fork"),
                ),
            )
        assertEquals(
            listOf(
                ev(Relation.FORK, forked),
                ad(Relation.FORK, article),
            ),
            event.links(),
        )
    }

    @Test
    fun malformedReferencesAreDroppedAndHexIsLowercased() {
        val event =
            note(
                arrayOf(
                    arrayOf("e", "not-an-id", "", "root"),
                    arrayOf("e", parent.uppercase(), "", "reply"),
                    arrayOf("p", "npub-is-not-hex"),
                    arrayOf("a", "30023:short:d"),
                    arrayOf("q", "nope"),
                    arrayOf("t", ""),
                ),
            )
        assertEquals(
            listOf(
                ev(Relation.ROOT, parent),
                ev(Relation.PARENT, parent),
            ),
            event.links(),
        )
    }
}
