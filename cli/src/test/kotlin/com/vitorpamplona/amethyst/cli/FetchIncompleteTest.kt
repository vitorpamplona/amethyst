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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `amy fetch --paginate` against a relay that serves the first page and refuses the next: the
 * walk delivered events and then gave up, so what was written is good but not the whole answer.
 * That used to exit 0 with only a `relay_errors` entry, and a script that read the exit code
 * took a partial export for a full one. Each output path keeps what arrived and fails
 * `incomplete` (exit 1), whose payload names the relay, how many events it delivered, and the
 * fetch's `count` (and `out`) so a rerun can pick up with `--until`.
 */
class FetchIncompleteTest {
    /** Serves a first page and refuses every page after it, as a policy refusal (no throttle wording, so no re-asks). */
    private class FirstPageOnlyPolicy : PassThroughPolicy() {
        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = if (cmd.filters.any { it.until != null }) PolicyResult.Rejected(REASON) else PolicyResult.Accepted(cmd)
    }

    private lateinit var engine: RelayEngine
    private lateinit var relay: KtorRelay
    private lateinit var notes: List<Event>

    @BeforeTest
    fun setup() {
        engine = RelayEngine(url = "ws://127.0.0.1:7784/".normalizeRelayUrl(), policyBuilder = { FirstPageOnlyPolicy() })
        relay = KtorRelay(engine, host = "127.0.0.1", port = 0).start()
        val signer = NostrSignerSync(KeyPair())
        notes = (1..3).map { signer.sign(TextNoteEvent.build("note $it", createdAt = 1_700_000_000L + it)) }
        runBlocking { notes.forEach { engine.store.insert(it) } }
    }

    @AfterTest
    fun teardown() {
        relay.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        engine.close()
    }

    @Test
    fun aStreamedWalkThatStopsPartwayKeepsItsEventsAndFailsIncomplete() {
        val r = amy("--json", "fetch", "--kind", "1", "--paginate", "--limit", "0", "--relay", relay.url, "--timeout", "3")

        assertEquals(1, r.exit, "a partial walk is not a success: ${r.stdout} ${r.stderr}")
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(notes.map { it.id }.toSet(), result["events"].map { it["id"].asText() }.toSet(), "what arrived is still written: $result")
        assertEquals("closed", result["relay_errors"][relay.url.normalized()]["reason"].asText(), "$result")
        assertIncomplete(jsonErrorOf(r), count = 3)
    }

    @Test
    fun anExportThatStopsPartwayKeepsTheFileAndFailsIncomplete() {
        val out = Files.createTempFile("amy-incomplete", ".jsonl").toFile()
        try {
            val r = amy("--json", "fetch", "--kind", "1", "--paginate", "--limit", "0", "--out", out.path, "--relay", relay.url, "--timeout", "3")

            assertEquals(1, r.exit, "${r.stdout} ${r.stderr}")
            assertEquals(
                notes.map { it.id }.toSet(),
                out
                    .readLines()
                    .filter { it.isNotBlank() }
                    .map { JsonNode.parse(it)["id"].asText() }
                    .toSet(),
            )
            assertEquals(3, JsonNode.parse(r.stdoutLines.single())["count"].asInt(), r.stdout)
            val error = jsonErrorOf(r)
            assertIncomplete(error, count = 3)
            assertEquals(out.path, error["out"].asText(), "$error")
        } finally {
            out.delete()
        }
    }

    @Test
    fun aBoundedWalkThatStopsPartwayPrintsItsEventsAndFailsIncomplete() {
        val r = amy("--json", "fetch", "--kind", "1", "--paginate", "--limit", "10", "--relay", relay.url, "--timeout", "3")

        assertEquals(1, r.exit, "${r.stdout} ${r.stderr}")
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(notes.sortedByDescending { it.createdAt }.map { it.id }, result["events"].map { it["id"].asText() }, "$result")
        assertIncomplete(jsonErrorOf(r), count = 3)
    }

    @Test
    fun aTextModeIncompleteSaysWhereTheWalkStopped() {
        val r = amy("fetch", "--kind", "1", "--paginate", "--limit", "0", "--relay", relay.url, "--timeout", "3")

        assertEquals(1, r.exit, r.stderr)
        val errorLine = r.stderr.lines().single { it.startsWith("error: incomplete") }
        assertTrue(errorLine.contains("closed the request: $REASON after 3 events"), errorLine)
        assertFalse(r.stderr.contains("warning: ${relay.url.normalized()}"), "the error names the relay; no second warning: ${r.stderr}")
    }

    private fun assertIncomplete(
        error: JsonNode,
        count: Int,
    ) {
        assertEquals("incomplete", error["error"].asText(), "$error")
        assertEquals(count, error["count"].asInt(), "$error")
        assertEquals(3, error["incomplete_relays"][relay.url.normalized()].asInt(), "$error")
        val refusal = error["relay_errors"][relay.url.normalized()]
        assertEquals("closed", refusal["reason"].asText(), "$error")
        assertEquals(REASON, refusal["message"].asText(), "$error")
    }

    private fun jsonErrorOf(r: CliResult): JsonNode = JsonNode.parse(r.stderr.lines().single { it.startsWith("{") })

    private fun String.normalized() = normalizeRelayUrl().url

    private companion object {
        const val REASON = "blocked: one page only"
    }
}
