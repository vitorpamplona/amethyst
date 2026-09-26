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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nipXXSql.NqlType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [nql] against an in-process relay behind NIP-42: the client authenticates and re-sends. */
class NostrClientNqlTest {
    @Test
    fun countsBehindAuth() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(12)
                val result = h.client.nql(AuthGatedRelayHarness.URL, "SELECT count(*) AS n FROM events WHERE kind = 1")
                assertEquals(listOf("n" to NqlType.INTEGER), result.columns.map { it.name to it.type })
                assertEquals(listOf(listOf(12L)), result.rows)
            }
        }

    @Test
    fun theRelayCapMarksTheAnswerTruncated() =
        runBlocking {
            AuthGatedRelayHarness(limits = RelayLimits(maxNqlRows = 5)).use { h ->
                h.preload(12)
                val result = h.client.nql(AuthGatedRelayHarness.URL, "SELECT content FROM events WHERE kind = ? ORDER BY created_at DESC", params = listOf(1L))
                assertEquals((0 until 5).map { listOf("gated-$it") }, result.rows)
                assertTrue(result.truncated)
            }
        }

    /**
     * A client-wide reconnect sweep (`reconnect(onlyIfChanged = false)`, debounced 200ms) drops
     * every socket. A slow signer keeps the query waiting on AUTH across it, so its answer dies
     * with the first socket every time and the query must be sent again on the second.
     */
    @Test
    fun survivesTheSocketDroppingBeforeTheAnswer() =
        runBlocking {
            AuthGatedRelayHarness(signDelayMs = 1_000).use { h ->
                h.preload(3)
                val sweep =
                    launch {
                        delay(100)
                        h.client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
                    }
                val result = h.client.nql(AuthGatedRelayHarness.URL, "SELECT count(*) AS n FROM events", timeoutMs = 10_000)
                sweep.join()
                assertEquals(listOf(listOf(3L)), result.rows)
            }
        }

    @Test
    fun filtersReadOverNql() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(12)
                val filter = Filter(kinds = listOf(1), limit = 5)
                val events = h.client.nqlQuery(AuthGatedRelayHarness.URL, filter)
                assertEquals((0 until 5).map { "gated-$it" }, events.map { it.content })
                assertTrue(events.all { it.verify() })
                assertEquals(12L, h.client.nqlCount(AuthGatedRelayHarness.URL, filter))
                assertEquals(events.map { it.id }, h.client.nqlIdsAndTimes(AuthGatedRelayHarness.URL, filter).map { it.id })
            }
        }

    @Test
    fun filtersPagePastTheRelayCap() =
        runBlocking {
            AuthGatedRelayHarness(limits = RelayLimits(maxNqlRows = 4)).use { h ->
                h.preload(11)
                val all = h.client.nqlQuery(AuthGatedRelayHarness.URL, Filter(kinds = listOf(1)))
                // Preloaded a second apart, but two can share a second: newest first, ties by id.
                assertEquals((0 until 11).map { "gated-$it" }.toSet(), all.map { it.content }.toSet())
                assertEquals(all.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }), all)
                assertTrue(all.all { it.verify() })
                assertEquals(7, h.client.nqlIdsAndTimes(AuthGatedRelayHarness.URL, Filter(kinds = listOf(1), limit = 7)).size)
            }
        }

    @Test
    fun refusalsSurfaceWithTheirPrefix() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                val e = assertFailsWith<NqlQueryException> { h.client.nql(AuthGatedRelayHarness.URL, "SELECT * FROM event_headers") }
                assertTrue(e.reason.startsWith("invalid:"), e.reason)
            }
        }

    @Test
    fun withoutAuthTheWallIsReported() =
        runBlocking {
            AuthGatedRelayHarness(attachAuthenticator = false).use { h ->
                val e = assertFailsWith<NqlQueryException> { h.client.nql(AuthGatedRelayHarness.URL, "SELECT 1 AS x") }
                assertTrue(e.reason.startsWith("auth-required:"), e.reason)
            }
        }
}
