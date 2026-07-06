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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustGraphBuilderTest {
    // Distinct valid 64-hex pubkeys.
    private fun pk(n: Int): HexKey = n.toString(16).padStart(64, '0')

    private val alice = pk(0xA1)
    private val bob = pk(0xB0)
    private val carol = pk(0xC0)
    private val dave = pk(0xD0)

    private val dummySig = "0".repeat(128)

    private fun contactList(
        author: HexKey,
        follows: List<HexKey>,
        createdAt: Long = 1000,
    ) = ContactListEvent(
        id = pk(author.hashCode() xor createdAt.toInt()),
        pubKey = author,
        createdAt = createdAt,
        tags = follows.map { arrayOf("p", it) }.toTypedArray(),
        content = "",
        sig = dummySig,
    )

    private fun muteList(
        author: HexKey,
        mutes: List<HexKey>,
        createdAt: Long = 1000,
    ) = MuteListEvent(
        id = pk(author.hashCode() xor createdAt.toInt() xor 0x5555),
        pubKey = author,
        createdAt = createdAt,
        tags = mutes.map { arrayOf("p", it) }.toTypedArray(),
        content = "",
        sig = dummySig,
    )

    private fun report(
        author: HexKey,
        reported: HexKey,
        createdAt: Long = 1000,
    ) = ReportEvent(
        id = pk(author.hashCode() xor reported.hashCode() xor createdAt.toInt()),
        pubKey = author,
        createdAt = createdAt,
        tags = arrayOf(arrayOf("p", reported, "spam")),
        content = "",
        sig = dummySig,
    )

    @Test
    fun buildsFollowMuteAndReportEdges() {
        val graph =
            TrustGraphBuilder.build(
                listOf(
                    contactList(alice, listOf(bob, carol)),
                    muteList(bob, listOf(dave)),
                    report(carol, dave),
                ),
            )

        assertEquals(
            setOf(TrustEdge(alice, TrustRelation.FOLLOW)),
            graph.incoming[bob]?.toSet(),
        )
        assertEquals(
            setOf(TrustEdge(alice, TrustRelation.FOLLOW)),
            graph.incoming[carol]?.toSet(),
        )
        assertEquals(
            setOf(TrustEdge(bob, TrustRelation.MUTE), TrustEdge(carol, TrustRelation.REPORT)),
            graph.incoming[dave]?.toSet(),
        )
    }

    @Test
    fun keepsOnlyLatestReplaceablePerAuthor() {
        val graph =
            TrustGraphBuilder.build(
                listOf(
                    contactList(alice, listOf(bob), createdAt = 1000),
                    contactList(alice, listOf(carol), createdAt = 2000),
                ),
            )
        // The newer list (follows carol) wins; the stale bob follow is gone.
        assertTrue(graph.incoming[bob].isNullOrEmpty())
        assertEquals(setOf(TrustEdge(alice, TrustRelation.FOLLOW)), graph.incoming[carol]?.toSet())
    }

    @Test
    fun dedupesRepeatedReports() {
        val graph =
            TrustGraphBuilder.build(
                listOf(
                    report(alice, dave, createdAt = 1000),
                    report(alice, dave, createdAt = 2000),
                ),
            )
        assertEquals(listOf(TrustEdge(alice, TrustRelation.REPORT)), graph.incoming[dave])
    }

    @Test
    fun dropsSelfEdges() {
        val graph = TrustGraphBuilder.build(listOf(contactList(alice, listOf(alice, bob))))
        assertTrue(graph.incoming[alice].isNullOrEmpty(), "a self-follow must not become an edge")
        assertEquals(setOf(TrustEdge(alice, TrustRelation.FOLLOW)), graph.incoming[bob]?.toSet())
    }
}
