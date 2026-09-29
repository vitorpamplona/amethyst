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
package com.vitorpamplona.quartz.nip22Comments

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.LinkProps
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip22CommentsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val root = "e1".repeat(32)
    private val parent = "e2".repeat(32)
    private val cited = "e3".repeat(32)
    private val rootAuthor = "b1".repeat(32)
    private val parentAuthor = "b2".repeat(32)
    private val bystander = "b3".repeat(32)

    private fun comment(
        tags: TagArray,
        content: String = "",
    ) = CommentEvent(id, me, 1L, tags, content, sig)

    private fun <P : LinkProps> link(
        relation: Relation<P>,
        target: LinkTarget,
        via: String,
    ) = Link(relation, target, via)

    @Test
    fun uppercaseIsTheRootScopeAndLowercaseTheParentItem() {
        val nevent = NEvent.create(cited, null, null, null)
        val event =
            comment(
                arrayOf(
                    arrayOf("E", root, "wss://relay.example/", rootAuthor),
                    arrayOf("K", "1"),
                    arrayOf("P", rootAuthor),
                    arrayOf("e", parent, "wss://relay.example/", parentAuthor),
                    arrayOf("k", "1111"),
                    arrayOf("p", parentAuthor),
                    arrayOf("p", bystander),
                    arrayOf("q", "30023:$bystander:post"),
                    arrayOf("t", "Bitcoin"),
                ),
                "see nostr:$nevent",
            )

        assertEquals(
            listOf(
                link(Relation.ROOT, LinkTarget.Event(root), "E"),
                link(Relation.TAG, LinkTarget.Tag("k", "1"), "K"),
                link(Relation.ROOT_AUTHOR, LinkTarget.User(rootAuthor), "P"),
                link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                link(Relation.TAG, LinkTarget.Tag("k", "1111"), "k"),
                link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
                // NIP-22: a p for a pubkey mentioned in the content is not the parent's author
                link(Relation.MENTION, LinkTarget.User(bystander), "p"),
                link(Relation.QUOTE, LinkTarget.Address("30023:$bystander:post"), "q"),
                link(Relation.HASHTAG, LinkTarget.Tag("t", "bitcoin"), "t"),
                link(Relation.MENTION, LinkTarget.Event(cited), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun externalScopesAreOneNodeWhicheverCaseNamedThem() {
        val event =
            comment(
                arrayOf(
                    arrayOf("I", "https://example.com/post"),
                    arrayOf("K", "web"),
                    arrayOf("i", "https://example.com/post"),
                    arrayOf("k", "web"),
                    // no parent author is knowable for an external parent
                    arrayOf("p", bystander),
                ),
            )

        assertEquals(
            listOf(
                link(Relation.ROOT, LinkTarget.Tag("i", "https://example.com/post"), "I"),
                link(Relation.TAG, LinkTarget.Tag("k", "web"), "K"),
                link(Relation.PARENT, LinkTarget.Tag("i", "https://example.com/post"), "i"),
                link(Relation.TAG, LinkTarget.Tag("k", "web"), "k"),
                link(Relation.MENTION, LinkTarget.User(bystander), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun aCommunityPostIsAlsoInItsCommunity() {
        val community = "34550:$parentAuthor:nostr"
        val event =
            comment(
                arrayOf(
                    arrayOf("A", community, "wss://relay.example/"),
                    arrayOf("K", "34550"),
                    arrayOf("P", parentAuthor),
                    arrayOf("a", community, "wss://relay.example/"),
                    arrayOf("k", "34550"),
                    arrayOf("p", parentAuthor),
                ),
            )

        assertEquals(
            listOf(
                link(Relation.ROOT, LinkTarget.Address(community), "A"),
                link(Relation.COMMUNITY, LinkTarget.Address(community), "A"),
                link(Relation.TAG, LinkTarget.Tag("k", "34550"), "K"),
                link(Relation.ROOT_AUTHOR, LinkTarget.User(parentAuthor), "P"),
                link(Relation.PARENT, LinkTarget.Address(community), "a"),
                link(Relation.TAG, LinkTarget.Tag("k", "34550"), "k"),
                // the parent is an address: its author is the coordinate's pubkey
                link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
            ),
            event.links(),
        )
    }
}
