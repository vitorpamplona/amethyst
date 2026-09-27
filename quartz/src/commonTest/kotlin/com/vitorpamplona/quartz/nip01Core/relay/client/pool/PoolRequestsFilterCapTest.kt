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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * relay.us.whitenoise.chat (strfry + policy) CLOSES any REQ with 4 or more filters
 * (`ERROR: bad req: filter validation failed: invalid number of filters: N`) and
 * advertises no max_filters in NIP-11. Marmot subscribes to group messages with one
 * `{kinds:[445], #h:[group]}` filter per group in one REQ, so an account with 4+
 * groups on that relay loaded no group messages at all.
 */
class PoolRequestsFilterCapTest {
    private val relay = NormalizedRelayUrl("wss://relay.us.whitenoise.chat/")
    private val other = NormalizedRelayUrl("wss://nos.lol/")

    private class RecordingRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        val sent = mutableListOf<Command>()

        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) {
            sent.add(cmd)
        }

        override fun sendIfConnected(cmd: Command) {
            sent.add(cmd)
        }

        override fun disconnect() = Unit
    }

    private fun groupFilters(n: Int) = (1..n).map { Filter(kinds = listOf(445), tags = mapOf("h" to listOf("g$it")), since = 1000L + it) }

    private fun sync(
        pool: PoolRequests,
        url: NormalizedRelayUrl,
    ): List<ReqCmd> {
        pool.onConnecting(url)
        val sent = mutableListOf<Command>()
        pool.syncState(url) { sent.add(it) }
        return sent.filterIsInstance<ReqCmd>()
    }

    @Test
    fun learnsTheCapFromTheRefusal() {
        assertEquals(3, RelayReqRefusals.parseMaxFilters("ERROR: bad req: filter validation failed: invalid number of filters: 4"))
        assertEquals(null, RelayReqRefusals.parseMaxFilters("ERROR: bad req: filter validation failed: kind not allowed: 21059"))
    }

    @Test
    fun perGroupFiltersAreMergedUnderTheCapAndSentAgainAtOnce() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("groups", mapOf(relay to groupFilters(5)), null)
            assertEquals(5, sync(pool, relay).single().filters.size)

            val client = RecordingRelayClient(relay)
            pool.onIncomingMessage(client, ClosedMessage("groups", "ERROR: bad req: filter validation failed: invalid number of filters: 5"))

            val retry =
                client.sent
                    .filterIsInstance<ReqCmd>()
                    .single()
                    .filters
            assertEquals(1, retry.size, "five filters that differ only in #h become one")
            assertEquals((1..5).map { "g$it" }.toSet(), retry.single().tags!!["h"]!!.toSet())
            assertEquals(listOf(445), retry.single().kinds)
            assertEquals(1001L, retry.single().since, "the earliest since, so no group loses history")
        }

    @Test
    fun filtersThatCannotMergeAreTrimmedToTheCap() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            // Kinds AND authors differ, so no two of these can be merged.
            val distinct = (1..5).map { Filter(kinds = listOf(it), authors = listOf("$it".repeat(64))) }
            pool.addOrUpdate("mixed", mapOf(relay to distinct), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("mixed", "ERROR: bad req: filter validation failed: invalid number of filters: 4"))

            val filters = sync(pool, relay).single().filters
            assertEquals(3, filters.size, "a partial REQ the relay accepts beats one it refuses")
        }

    @Test
    fun filtersWithALimitAreNotMerged() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            val limited = (1..5).map { Filter(kinds = listOf(1), authors = listOf("a$it"), limit = 20) }
            pool.addOrUpdate("limited", mapOf(relay to limited), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("limited", "ERROR: bad req: filter validation failed: invalid number of filters: 4"))

            val filters = sync(pool, relay).single().filters
            assertEquals(3, filters.size)
            assertTrue(filters.all { it.authors!!.size == 1 }, "merging would change what each limit means")
        }

    @Test
    fun theCapOnlyAppliesToTheRelayThatRefused() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("groups", mapOf(relay to groupFilters(5), other to groupFilters(5)), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("groups", "ERROR: bad req: filter validation failed: invalid number of filters: 5"))

            assertEquals(5, sync(pool, other).single().filters.size)
        }

    @Test
    fun anAccountsOwnListsMergeByKind() {
        // Seen on device: the account's own lists (mute sets, bookmarks, pins, …) as
        // several filters on the same author that differ only in their kinds.
        val me = listOf("a".repeat(64))
        val lists =
            listOf(
                Filter(kinds = listOf(30000, 39089, 10000), authors = me),
                Filter(kinds = listOf(10003, 30001, 30003), authors = me),
                Filter(kinds = listOf(1984), authors = me),
            )
        val merged = RelayReqRefusals.mergeForCap(lists).single()
        assertEquals(setOf(30000, 39089, 10000, 10003, 30001, 30003, 1984), merged.kinds!!.toSet())
        assertEquals(me, merged.authors)
    }

    @Test
    fun twoDifferencesAtOnceAreNotMerged() {
        // Different kinds AND different authors: the union would ask for pairs no filter wanted.
        val a = Filter(kinds = listOf(1), authors = listOf("a".repeat(64)))
        val b = Filter(kinds = listOf(7), authors = listOf("b".repeat(64)))
        assertEquals(2, RelayReqRefusals.mergeForCap(listOf(a, b)).size)
    }
}
