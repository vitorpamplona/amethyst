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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `amy fetch --paginate` with a NIP-50 `search`. A search with words is relevance-ranked, so
 * the walk takes the relay's first page and stops (PagedFetchResult.End.UNPAGEABLE). That stop
 * used to be silent, so it is reported: an `unpaged_relays` entry and a warning that names
 * `amy sync`. A search of only `key:value` extensions, such as search.brainstorm.world's
 * `include:spam` token, has nothing to rank and pages to the end; it used to return the
 * relay's first 5,000 of 35,578 zap receipts.
 */
class FetchUnpagedSearchTest {
    private lateinit var engine: RelayEngine
    private lateinit var relay: KtorRelay

    @BeforeTest
    fun setup() {
        engine = RelayEngine(url = "ws://127.0.0.1:7783/".normalizeRelayUrl())
        relay = KtorRelay(engine, host = "127.0.0.1", port = 0).start()
        val note = NostrSignerSync(KeyPair()).sign(TextNoteEvent.build("hello from the search relay"))
        runBlocking { engine.store.insert(note) }
    }

    @AfterTest
    fun teardown() {
        relay.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        engine.close()
    }

    @Test
    fun anUnboundedSearchWalkReportsTheRelayItCouldNotPage() {
        val r = amy("--json", "fetch", "--kind", "1", "--search", "hello", "--paginate", "--limit", "0", "--relay", relay.url, "--timeout", "5")

        assertUnpaged(r)
    }

    @Test
    fun aBoundedSearchWalkReportsItToo() {
        val r = amy("--json", "fetch", "--kind", "1", "--search", "hello", "--paginate", "--limit", "100", "--relay", relay.url, "--timeout", "5")

        assertUnpaged(r)
    }

    /** A ranked search with no hits is still one page deep: the empty answer is reported the same way on both paths. */
    @Test
    fun aSearchWithNoHitsStillReportsTheRelay() {
        for (limit in listOf("0", "100")) {
            val r = amy("--json", "fetch", "--kind", "1", "--search", "absent", "--paginate", "--limit", limit, "--relay", relay.url, "--timeout", "5")

            assertEquals(0, r.exit, r.stderr)
            val result = JsonNode.parse(r.stdoutLines.single())
            assertEquals(0, result["count"].asInt(), "--limit $limit: $result")
            assertEquals(listOf(relay.url.normalizeRelayUrl().url), result["unpaged_relays"]?.map { it.asText() }, "--limit $limit: $result")
        }
    }

    /** `include:spam` is an access token with no words to rank by, so the walk pages it like a plain filter. */
    @Test
    fun aSearchOfOnlyExtensionsIsPagedToTheEnd() {
        val r = amy("--json", "fetch", "--kind", "1", "--search", "include:spam", "--paginate", "--limit", "0", "--relay", relay.url, "--timeout", "5")

        assertEquals(0, r.exit, r.stderr)
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(1, result["count"].asInt(), "$result ${r.stderr}")
        assertFalse(result.has("unpaged_relays"), "nothing to rank, so nothing left unpaged: $result")
        assertFalse(r.stderr.contains("warning:"), r.stderr)
    }

    @Test
    fun aWalkWithoutSearchHasNoUnpagedRelays() {
        val r = amy("--json", "fetch", "--kind", "1", "--paginate", "--limit", "0", "--relay", relay.url, "--timeout", "5")

        assertEquals(0, r.exit, r.stderr)
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(1, result["count"].asInt(), "$result ${r.stderr}")
        assertFalse(result.has("unpaged_relays"), "a time walk pages to the end: $result")
        assertFalse(r.stderr.contains("warning:"), r.stderr)
    }

    private fun assertUnpaged(r: CliResult) {
        assertEquals(0, r.exit, r.stderr)
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(1, result["count"].asInt(), "the search still returns its hits: $result ${r.stderr}")
        val unpaged = result["unpaged_relays"]
        assertTrue(unpaged != null && unpaged.isArray, "the relay that could not be paged is listed: $result")
        assertEquals(listOf(relay.url.normalizeRelayUrl().url), unpaged.map { it.asText() })
        assertTrue(r.stderr.contains("amy sync"), "the warning names the command that downloads everything: ${r.stderr}")
        assertFalse(result.has("relay_errors"), "the relay answered; it is not an error: $result")
    }
}
