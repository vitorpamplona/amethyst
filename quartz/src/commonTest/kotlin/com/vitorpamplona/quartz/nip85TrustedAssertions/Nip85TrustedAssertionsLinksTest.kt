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
package com.vitorpamplona.quartz.nip85TrustedAssertions

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.ServiceProps
import com.vitorpamplona.quartz.graph.props.SubjectProps
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip85TrustedAssertionsLinksTest {
    private val id = "0".repeat(64)
    private val provider = "c1".repeat(32)
    private val sig = "0".repeat(128)

    private val subject = "b1".repeat(32)
    private val note = "e1".repeat(32)

    @Test
    fun aUserCardScoresItsSubject() {
        val event =
            UserAssertionEvent(
                id,
                provider,
                1L,
                arrayOf(
                    arrayOf("d", subject),
                    // only a relay hint for the subject
                    arrayOf("p", subject, "wss://relay.example/"),
                    arrayOf("rank", "89"),
                    arrayOf("followers", "1200"),
                    arrayOf("hops", "2"),
                    arrayOf("first_created_at", "1672531200"),
                    arrayOf("zap_amt_recd", "not-a-number"),
                    arrayOf("rank", "10"),
                    arrayOf("t", "Bitcoin"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(
                    Relation.SUBJECT,
                    LinkTarget.User(subject),
                    "d",
                    SubjectProps(rank = 89, followers = 1200, hops = 2, firstCreatedAt = 1672531200L),
                ),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "bitcoin"), "t"),
            ),
            event.links(),
        )
    }

    @Test
    fun anEventAssertionScoresItsEvent() {
        val event =
            EventAssertionEvent(
                id,
                provider,
                1L,
                arrayOf(
                    arrayOf("d", note),
                    arrayOf("e", note, "wss://relay.example/"),
                    arrayOf("rank", "70"),
                    arrayOf("comment_cnt", "12"),
                    arrayOf("zap_amount", "21000"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(
                    Relation.SUBJECT,
                    LinkTarget.Event(note),
                    "d",
                    SubjectProps(rank = 70, commentCount = 12, zapAmount = 21000L),
                ),
            ),
            event.links(),
        )
    }

    @Test
    fun anAddressAssertionScoresItsAddress() {
        val address = "30023:$subject:article"
        val event = AddressableAssertionEvent(id, provider, 1L, arrayOf(arrayOf("d", address)), "", sig)
        assertEquals(listOf(Link(Relation.SUBJECT, LinkTarget.Address(address), "d")), event.links())
    }

    @Test
    fun anExternalIdAssertionScoresTheNip73Id() {
        val event =
            ExternalIdAssertionEvent(
                id,
                provider,
                1L,
                arrayOf(
                    arrayOf("d", "isbn:9780765382030"),
                    arrayOf("k", "isbn"),
                    arrayOf("reaction_cnt", "5"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.SUBJECT, LinkTarget.Tag("i", "isbn:9780765382030"), "d", SubjectProps(reactionCount = 5)),
                Link(Relation.TAG, LinkTarget.Tag("k", "isbn"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aProviderListNamesOneProviderPerService() {
        val other = "c2".repeat(32)
        val event =
            TrustProviderListEvent(
                id,
                "f".repeat(64),
                1L,
                arrayOf(
                    arrayOf("30382:rank", provider, "wss://scores.example/"),
                    arrayOf("30382:followers", provider, "wss://scores.example/"),
                    arrayOf("30383:rank", other, "wss://other.example/"),
                    // not a NIP-85 assertion kind
                    arrayOf("1:rank", other, "wss://other.example/"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.SERVICE_PROVIDER, LinkTarget.User(provider), "30382:rank", ServiceProps("30382:rank")),
                Link(Relation.SERVICE_PROVIDER, LinkTarget.User(provider), "30382:followers", ServiceProps("30382:followers")),
                Link(Relation.SERVICE_PROVIDER, LinkTarget.User(other), "30383:rank", ServiceProps("30383:rank")),
            ),
            event.links(),
        )
    }
}
