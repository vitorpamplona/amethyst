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
