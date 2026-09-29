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
package com.vitorpamplona.quartz.nip18Reposts

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip18RepostsLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val reposted = "e1".repeat(32)
    private val earlier = "e2".repeat(32)
    private val author = "b1".repeat(32)
    private val bystander = "b2".repeat(32)
    private val address = "30023:$author:article"

    @Test
    fun aRepostPointsAtTheLastEventAndItsAuthor() {
        val event =
            RepostEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", earlier),
                    arrayOf("p", bystander),
                    arrayOf("p", author, "wss://relay.example/"),
                    arrayOf("e", reposted, "wss://relay.example/", author),
                    arrayOf("k", "1"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.REPOSTED, LinkTarget.Event(reposted), "e"),
                Link(Relation.REPOSTED_AUTHOR, LinkTarget.User(author), "p"),
                Link(Relation.MENTION, LinkTarget.Event(earlier), "e"),
                Link(Relation.MENTION, LinkTarget.User(bystander), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aGenericRepostOfAnAddressableNamesItsVersionAndItsAddress() {
        val event =
            GenericRepostEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("k", "30023"),
                    arrayOf("p", author),
                    arrayOf("e", reposted),
                    arrayOf("a", address),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.REPOSTED, LinkTarget.Event(reposted), "e"),
                Link(Relation.REPOSTED, LinkTarget.Address(address), "a"),
                Link(Relation.REPOSTED_AUTHOR, LinkTarget.User(author), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "30023"), "k"),
            ),
            event.links(),
        )
    }
}
