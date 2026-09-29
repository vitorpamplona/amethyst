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
package com.vitorpamplona.quartz.nip66RelayMonitor

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.monitor.RelayMonitorEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip66RelayMonitorLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val monitor = "1".repeat(64)

    @Test
    fun monitorLinksItsGeohash() {
        val event =
            RelayMonitorEvent(
                id,
                monitor,
                1,
                arrayOf(arrayOf("frequency", "3600"), arrayOf("c", "ws"), arrayOf("g", "u4pr")),
                "",
                sig,
            )
        assertEquals(listOf(Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g")), event.links())
    }

    @Test
    fun discoveryLinksTopicsGeohashesAndKindsButNotTheRelayUrl() {
        val event =
            RelayDiscoveryEvent(
                id,
                monitor,
                1,
                arrayOf(
                    arrayOf("d", "wss://relay.example/"),
                    arrayOf("rtt-open", "234"),
                    arrayOf("n", "clearnet"),
                    arrayOf("t", "Bitcoin"),
                    arrayOf("g", "u4pr"),
                    arrayOf("k", "1"),
                    arrayOf("r", "wss://other.example/"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "bitcoin"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun discoveryDoesNotLinkTheKindsARelayRejects() {
        val event =
            RelayDiscoveryEvent(
                id,
                monitor,
                1,
                arrayOf(arrayOf("d", "wss://relay.example/"), arrayOf("k", "!4"), arrayOf("k", "1")),
                "",
                sig,
            )
        assertEquals(listOf(Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k")), event.links())
    }
}
