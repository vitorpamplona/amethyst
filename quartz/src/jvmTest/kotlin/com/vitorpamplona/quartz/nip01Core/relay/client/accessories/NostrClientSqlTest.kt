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

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [sql] against an in-process relay behind NIP-42: the client authenticates,
 * re-sends, and pulls every page with FETCH.
 */
class NostrClientSqlTest {
    @Test
    fun countsBehindAuth() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(12)
                val result = h.client.sql(AuthGatedRelayHarness.URL, "SELECT count(*) AS n FROM events WHERE kind = 1")
                assertEquals(listOf("n"), result.columns)
                assertEquals(listOf(listOf(12L)), result.rows)
            }
        }

    @Test
    fun pagesThroughEverything() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(12)
                val result =
                    h.client.sql(
                        AuthGatedRelayHarness.URL,
                        "SELECT content FROM events WHERE kind = ? ORDER BY created_at DESC",
                        params = listOf(1L),
                        pageSize = 5,
                    )
                assertEquals((0 until 12).map { listOf("gated-$it") }, result.rows)
            }
        }

    @Test
    fun streamsPageByPage() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(7)
                var columns = emptyList<String>()
                val seen = ArrayList<Any?>()
                h.client.sqlStream(
                    AuthGatedRelayHarness.URL,
                    "SELECT content FROM events WHERE kind = 1",
                    pageSize = 2,
                    onColumns = { columns = it },
                ) { seen.add(it[0]) }
                assertEquals(listOf("content"), columns)
                assertEquals(7, seen.size)
            }
        }

    /**
     * A client-wide reconnect sweep (`reconnect(onlyIfChanged = false)`, debounced 200ms) drops
     * every socket. A slow signer keeps the query waiting on AUTH across it, so its cursor dies
     * with the first socket every time and the query must start over on the second.
     */
    @Test
    fun survivesTheSocketDroppingBeforeTheFirstRow() =
        runBlocking {
            AuthGatedRelayHarness(signDelayMs = 1_000).use { h ->
                h.preload(3)
                val sweep =
                    launch {
                        delay(100)
                        h.client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
                    }
                val result = h.client.sql(AuthGatedRelayHarness.URL, "SELECT count(*) FROM events", idleTimeoutMs = 10_000)
                sweep.join()
                assertEquals(listOf(listOf(3L)), result.rows)
            }
        }

    @Test
    fun filtersReadOverSql() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                h.preload(12)
                val filter = Filter(kinds = listOf(1), limit = 5)
                val events = h.client.sqlQuery(AuthGatedRelayHarness.URL, filter)
                assertEquals((0 until 5).map { "gated-$it" }, events.map { it.content })
                assertTrue(events.all { it.verify() })
                assertEquals(12L, h.client.sqlCount(AuthGatedRelayHarness.URL, filter))
                assertEquals(events.map { it.id }, h.client.sqlIdsAndTimes(AuthGatedRelayHarness.URL, filter).map { it.id })
            }
        }

    @Test
    fun refusalsSurfaceWithTheirPrefix() =
        runBlocking {
            AuthGatedRelayHarness().use { h ->
                val e = assertFailsWith<SqlQueryException> { h.client.sql(AuthGatedRelayHarness.URL, "SELECT * FROM event_headers") }
                assertTrue(e.reason.startsWith("invalid:"), e.reason)
            }
        }

    @Test
    fun withoutAuthTheWallIsReported() =
        runBlocking {
            AuthGatedRelayHarness(attachAuthenticator = false).use { h ->
                val e = assertFailsWith<SqlQueryException> { h.client.sql(AuthGatedRelayHarness.URL, "SELECT 1") }
                assertTrue(e.reason.startsWith("auth-required:"), e.reason)
            }
        }
}
