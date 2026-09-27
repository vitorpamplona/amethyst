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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorAdvertisement
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding coordinators in a relay's worth of MCP announcements.
 *
 * Three things here fail as a plausible-looking list rather than as an error,
 * which is why they are tested: an unrelated MCP server presented as a
 * coordinator, a coordinator credited with only one of the relays that carry
 * it, and a lagging relay's older announcement overwriting a newer one.
 */
class CordnCoordinatorDiscoveryTest {
    private val relayA = relay("wss://a.example")
    private val relayB = relay("wss://b.example")

    @Test
    fun `a server serving the eleven tools is a coordinator`() {
        val found = discovery().coordinatorsIn(announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS))

        assertEquals(1, found.size)
        assertEquals(COORDINATOR, found[0].pubKey)
        assertEquals(listOf(relayA), found[0].relays)
        assertEquals(CoordinatorConfig.Origin.ANNOUNCEMENT, found[0].toConfig().origin)
    }

    @Test
    fun `an unrelated MCP server is not`() {
        // The case this whole filter exists for: the public relays carry far
        // more non-cordn MCP servers than coordinators, and nothing on the
        // announcement distinguishes them except the tool names.
        val found =
            discovery().coordinatorsIn(
                announcementPair(relayA, OTHER_SERVER, toolNames = listOf("search", "fetch_page")),
            )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `a server missing one of the eleven is not`() {
        val found =
            discovery().coordinatorsIn(
                announcementPair(relayA, OTHER_SERVER, toolNames = CORDN_TOOLS - "msg_sub_many"),
            )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `serving more than the eleven still counts`() {
        val found =
            discovery().coordinatorsIn(
                announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS + "vendor_extra"),
            )

        assertEquals(1, found.size)
        assertTrue("vendor_extra" in found[0].tools)
    }

    @Test
    fun `a bare server announcement with no tool list is not a coordinator`() {
        // A kind 11316 says who a server claims to be, never what it serves.
        val found = discovery().coordinatorsIn(listOf(relayA to serverAnnouncement(COORDINATOR, name = "looks real")))

        assertTrue(found.isEmpty())
    }

    @Test
    fun `every relay that carried an announcement is kept`() {
        // The reachable relay set is the one thing a CEP-6 announcement does
        // not carry, so losing a relay here is losing the only routing
        // information discovery produces.
        val found =
            discovery().coordinatorsIn(
                announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS) +
                    announcementPair(relayB, COORDINATOR, toolNames = CORDN_TOOLS),
            )

        assertEquals(1, found.size)
        assertEquals(listOf(relayA, relayB), found[0].relays)
    }

    @Test
    fun `a lagging relay's older copy does not win`() {
        val newest =
            announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS, createdAt = 2_000, name = "current")
        val stale =
            announcementPair(relayB, COORDINATOR, toolNames = CORDN_TOOLS, createdAt = 1_000, name = "outdated")

        val found = discovery().coordinatorsIn(stale + newest)

        assertEquals("current", found[0].surface.name)
        assertEquals(2_000L, found[0].announcedAt)
    }

    @Test
    fun `the announced name is never copied into the user's label`() {
        // A label is what the user calls a coordinator. The announced name is
        // what the coordinator calls itself, and a coordinator cannot prove a
        // name (spec/00.md §8.5).
        val found = discovery().coordinatorsIn(announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS, name = "Official"))

        assertEquals("Official", found[0].surface.name)
        assertNull(found[0].toConfig().label)
    }

    @Test
    fun `results are newest first`() {
        val found =
            discovery().coordinatorsIn(
                announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS, createdAt = 100) +
                    announcementPair(relayA, SECOND_COORDINATOR, toolNames = CORDN_TOOLS, createdAt = 900),
            )

        assertEquals(listOf(SECOND_COORDINATOR, COORDINATOR), found.map { it.pubKey })
    }

    @Test
    fun `each relay is queried in its own call`() =
        runTest {
            // fetchAllWithHooks deduplicates by event id across the relays in
            // one call, so a single multi-relay call would credit a shared
            // announcement to whichever relay answered first and drop the
            // others. One call per relay scopes that dedup.
            val client = ServingClient(this)
            client.serve(relayA, announcementPair(relayA, COORDINATOR, toolNames = CORDN_TOOLS).map { it.second })
            client.serve(relayB, announcementPair(relayB, COORDINATOR, toolNames = CORDN_TOOLS).map { it.second })

            val result = CordnCoordinatorDiscovery(client).discover(setOf(relayA, relayB))

            assertEquals(setOf(setOf(relayA), setOf(relayB)), client.requestedRelaySets.toSet())
            assertEquals(1, result.coordinators.size)
            assertEquals(setOf(relayA, relayB), result.coordinators[0].relays.toSet())
            assertTrue(result.unreachable.isEmpty())
        }

    @Test
    fun `a relay that never answers is reported, not silently empty`() =
        runTest {
            // An empty list means "nobody is announcing" only when every relay
            // answered. Conflating the two would present a failed scan as a
            // network with no coordinators on it.
            val client = ServingClient(this)
            client.serve(relayA, emptyList())

            val result = CordnCoordinatorDiscovery(client).discover(setOf(relayA, relayB), idleTimeoutMs = 50)

            assertTrue(result.coordinators.isEmpty())
            assertEquals(setOf(relayB), result.unreachable)
        }

    @Test
    fun `no relays is not a query`() =
        runTest {
            val client = ServingClient(this)

            val result = CordnCoordinatorDiscovery(client).discover(emptySet())

            assertTrue(result.coordinators.isEmpty())
            assertTrue(client.requestedRelaySets.isEmpty())
        }

    @Test
    fun `the required toolset is derived from the protocol, not retyped`() {
        // If a twelfth tool is ever added to CoordinatorMethod, this predicate
        // must tighten with it rather than keep matching an older server.
        assertEquals(11, CoordinatorAdvertisement.REQUIRED_TOOLS.size)
        assertEquals(CORDN_TOOLS.toSet(), CoordinatorAdvertisement.REQUIRED_TOOLS)
        assertFalse("kp_publish" in CoordinatorAdvertisement.missingFrom(tools(CORDN_TOOLS)))
    }

    private fun discovery() = CordnCoordinatorDiscovery(EmptyNostrClient())

    private fun tools(names: List<String>) =
        com.vitorpamplona.quartz.contextvm.cep06Announcements.AnnouncedTools
            .parseOrNull(toolsJson(names))!!

    /** Serves a fixed set of events per relay, then EOSEs. */
    private class ServingClient(
        private val scope: CoroutineScope,
    ) : INostrClient by EmptyNostrClient() {
        private val byRelay = mutableMapOf<NormalizedRelayUrl, List<Event>>()
        val requestedRelaySets = mutableListOf<Set<NormalizedRelayUrl>>()

        fun serve(
            relay: NormalizedRelayUrl,
            events: List<Event>,
        ) {
            byRelay[relay] = events
        }

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            requestedRelaySets += filters.keys
            val target = listener ?: return
            scope.launch(Dispatchers.Unconfined) {
                filters.keys.forEach { relay ->
                    // A relay with nothing registered never answers at all,
                    // which is the "stalled" case discover must report.
                    val events = byRelay[relay] ?: return@forEach
                    events.forEach { target.onEvent(it, false, relay, null) }
                    target.onEose(relay, null)
                }
            }
        }

        override fun unsubscribe(subId: String) = Unit
    }

    companion object {
        private val COORDINATOR = "aa".repeat(32)
        private val SECOND_COORDINATOR = "bb".repeat(32)
        private val OTHER_SERVER = "cc".repeat(32)

        private val CORDN_TOOLS =
            listOf(
                "kp_publish",
                "kp_remove",
                "kp_list",
                "kp_take",
                "welcome_store",
                "welcome_take",
                "join_request_store",
                "join_request_take_many",
                "msg_post",
                "msg_fetch_many",
                "msg_sub_many",
            )

        private fun relay(url: String) = RelayUrlNormalizer.normalize(url)

        private fun toolsJson(names: List<String>) =
            names.joinToString(
                prefix = """{"tools":[""",
                postfix = "]}",
            ) { """{"name":"$it","inputSchema":{"type":"object"}}""" }

        /** The 11316 + 11317 pair a real coordinator publishes. */
        private fun announcementPair(
            relay: NormalizedRelayUrl,
            pubKey: HexKey,
            toolNames: List<String>,
            createdAt: Long = 1_000,
            name: String = "a coordinator",
        ) = listOf(
            relay to serverAnnouncement(pubKey, name, createdAt),
            relay to
                event(
                    pubKey = pubKey,
                    kind = CvmKinds.TOOLS_LIST,
                    createdAt = createdAt,
                    content = toolsJson(toolNames),
                ),
        )

        private fun serverAnnouncement(
            pubKey: HexKey,
            name: String,
            createdAt: Long = 1_000,
        ) = event(
            pubKey = pubKey,
            kind = CvmKinds.SERVER_ANNOUNCEMENT,
            createdAt = createdAt,
            content = """{"protocolVersion":"2025-11-25"}""",
            tags = arrayOf(arrayOf("name", name)),
        )

        /**
         * Unsigned: nothing in discovery verifies a signature, because the
         * relay already did and an announcement proves nothing either way.
         */
        private fun event(
            pubKey: HexKey,
            kind: Int,
            createdAt: Long,
            content: String,
            tags: Array<Tag> = emptyArray(),
        ) = Event(
            id = "${kind}_${pubKey.take(4)}_$createdAt",
            pubKey = pubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = "00".repeat(32),
        )
    }
}
