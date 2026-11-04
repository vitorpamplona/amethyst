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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `amy fetch` against a relay that answers a REQ with CLOSED, the way relay.zapstore.dev
 * answers any `limit` above ~50 with `CLOSED "filters are too vague"` and no events. The
 * refusal must reach the caller: a `relay_errors` entry in the result, and a
 * `no_relay_served` error when no relay served the request, never a silent `count: 0`.
 *
 * Two in-process geode relays: [refusing] runs [TooVaguePolicy]; [serving] is a plain relay
 * holding one signed note.
 */
class FetchRelayErrorsTest {
    /** Refuses any filter asking for more than 50 events, like relay.zapstore.dev. */
    private class TooVaguePolicy : PassThroughPolicy() {
        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = if (cmd.filters.any { (it.limit ?: 0) > 50 }) PolicyResult.Rejected(REASON) else PolicyResult.Accepted(cmd)
    }

    private lateinit var refusingEngine: RelayEngine
    private lateinit var servingEngine: RelayEngine
    private lateinit var refusing: KtorRelay
    private lateinit var serving: KtorRelay
    private lateinit var note: Event

    @BeforeTest
    fun setup() {
        // Placeholder URLs: the engines only need a valid identity, the ports come from autobind.
        refusingEngine = RelayEngine(url = "ws://127.0.0.1:7781/".normalizeRelayUrl(), policyBuilder = { TooVaguePolicy() })
        servingEngine = RelayEngine(url = "ws://127.0.0.1:7782/".normalizeRelayUrl())
        refusing = KtorRelay(refusingEngine, host = "127.0.0.1", port = 0).start()
        serving = KtorRelay(servingEngine, host = "127.0.0.1", port = 0).start()

        note = NostrSignerSync(KeyPair()).sign(TextNoteEvent.build("hello from the serving relay"))
        runBlocking { servingEngine.store.insert(note) }
    }

    @AfterTest
    fun teardown() {
        refusing.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        serving.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        refusingEngine.close()
        servingEngine.close()
    }

    @Test
    fun aRelayThatClosesTheReqIsReportedAsNoRelayServed() {
        val r = amy("--json", "fetch", "--kind", "1", "--limit", "100", "--relay", refusing.url, "--timeout", "5")

        assertEquals(1, r.exit, "a refusal is not an empty success: ${r.stderr}")
        assertTrue(r.stdout.isBlank(), "errors must not write stdout")
        val error = jsonErrorOf(r)
        assertEquals("no_relay_served", error["error"].asText())
        assertTrue(error["detail"].asText().contains(REASON), "the detail names the relay's reason: $error")
        assertRefusal(error["relay_errors"], refusing.url)
        assertEquals(listOf(r.stderr.trim()), r.stderr.trim().lines(), "the error is all of stderr: ${r.stderr}")
    }

    // Text mode used to append the payload as a Kotlin map: `(relay_errors={ws://…={reason=closed, …}})`.
    @Test
    fun aTextModeRefusalIsReadable() {
        val r = amy("fetch", "--kind", "1", "--limit", "100", "--relay", refusing.url, "--timeout", "5")

        assertEquals(1, r.exit, r.stderr)
        val lines = r.stderr.lines()
        val errorLine = lines.indexOfFirst { it.startsWith("error: no_relay_served") }
        assertTrue(errorLine >= 0, r.stderr)
        assertFalse(r.stderr.contains("={"), "no raw map dump: ${r.stderr}")
        assertEquals("  relay_errors:", lines[errorLine + 1], r.stderr)
        assertTrue(lines.drop(errorLine + 2).any { it.trim() == "reason:  closed" }, r.stderr)
        assertTrue(lines.drop(errorLine + 2).any { it.trim() == "message: $REASON" }, r.stderr)
    }

    @Test
    fun theRefusalIsReportedWhenPaginatingToo() {
        val r = amy("--json", "fetch", "--kind", "1", "--limit", "100", "--paginate", "--relay", refusing.url, "--timeout", "5")

        assertEquals(1, r.exit, r.stderr)
        val error = jsonErrorOf(r)
        assertEquals("no_relay_served", error["error"].asText())
        assertRefusal(error["relay_errors"], refusing.url)
    }

    // A relay that takes the connection and never answers came back as `count: 0`, exit 0, with no
    // warning: indistinguishable from a relay that holds nothing.
    @Test
    fun aRelaySilentThroughAPaginatedWalkIsReportedAsNoRelayServed() {
        SilentRelay().use { silent ->
            val r = amy("--json", "fetch", "--kind", "1", "--paginate", "--limit", "0", "--relay", silent.url, "--timeout", "2")

            assertEquals(1, r.exit, "silence is not an empty success: ${r.stdout} ${r.stderr}")
            val error = jsonErrorOf(r)
            assertEquals("no_relay_served", error["error"].asText())
            assertEquals("timeout", error["relay_errors"][silent.url.normalized()]["reason"].asText(), "$error")
        }
    }

    // The single-REQ fetch had the same hole: a relay that never sent EOSE was simply left out.
    @Test
    fun aRelaySilentThroughAPlainFetchIsReportedAsNoRelayServed() {
        SilentRelay().use { silent ->
            val r = amy("--json", "fetch", "--kind", "1", "--relay", silent.url, "--timeout", "2")

            assertEquals(1, r.exit, "silence is not an empty success: ${r.stdout} ${r.stderr}")
            val error = jsonErrorOf(r)
            assertEquals("no_relay_served", error["error"].asText())
            assertEquals("timeout", error["relay_errors"][silent.url.normalized()]["reason"].asText(), "$error")
        }
    }

    @Test
    fun aServingRelayStillSucceedsAndListsTheOneThatStayedSilent() {
        SilentRelay().use { silent ->
            val r = amy("--json", "fetch", "--kind", "1", "--relay", "${silent.url},${serving.url}", "--timeout", "2")

            assertEquals(0, r.exit, r.stderr)
            val result = JsonNode.parse(r.stdoutLines.single())
            assertEquals(1, result["count"].asInt(), "$result")
            assertEquals("timeout", result["relay_errors"][silent.url.normalized()]["reason"].asText(), "$result")
            assertNull(result["relay_errors"][serving.url.normalized()].element, "the serving relay answered: $result")
            assertTrue(r.stderr.contains("warning: ${silent.url.normalized()} stopped answering"), r.stderr)
        }
    }

    @Test
    fun aServingRelayStillSucceedsAndListsTheOneThatRefused() {
        val r = amy("--json", "fetch", "--kind", "1", "--limit", "100", "--relay", "${refusing.url},${serving.url}", "--timeout", "5")

        assertEquals(0, r.exit, r.stderr)
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(1, result["count"].asInt(), "the serving relay's note: $result ${r.stderr}")
        assertEquals(note.id, result["events"][0]["id"].asText())

        val errors = result["relay_errors"]
        assertEquals(1, errors.size(), "only the refusing relay is listed: $errors")
        assertRefusal(errors, refusing.url)
        assertNull(errors[serving.url.normalized()].element, "the serving relay answered")
        assertTrue(r.stderr.contains("closed the request: $REASON"), "a human-readable warning on stderr: ${r.stderr}")
    }

    @Test
    fun aRelayThatAnswersHasNoRelayErrors() {
        // The same relay serves a limit it accepts: it EOSEs with nothing, which is an answer.
        val r = amy("--json", "fetch", "--kind", "1", "--limit", "50", "--relay", refusing.url, "--timeout", "5")

        assertEquals(0, r.exit, r.stderr)
        val result = JsonNode.parse(r.stdoutLines.single())
        assertEquals(0, result["count"].asInt())
        assertFalse(result.has("relay_errors"), "no relay refused: $result")
        assertFalse(r.stderr.contains("warning:"), r.stderr)
    }

    private fun jsonErrorOf(r: CliResult): JsonNode {
        val line = r.stderr.lines().single { it.startsWith("{") }
        return JsonNode.parse(line)
    }

    private fun assertRefusal(
        relayErrors: JsonNode?,
        relayUrl: String,
    ) {
        val entry = relayErrors?.get(relayUrl.normalized())
        assertTrue(entry != null, "expected ${relayUrl.normalized()} in relay_errors: $relayErrors")
        assertEquals("closed", entry["reason"].asText())
        assertEquals(REASON, entry["message"].asText())
    }

    /** Completes the WebSocket handshake of every connection, then reads and never answers. */
    private class SilentRelay : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
        val url = "ws://127.0.0.1:${server.localPort}/"

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    sockets.add(socket)
                    thread(isDaemon = true) { serve(socket) }
                }
            }
        }

        private fun serve(socket: Socket) {
            runCatching {
                val input = socket.getInputStream().bufferedReader()
                val key =
                    generateSequence { input.readLine() }
                        .takeWhile { it.isNotEmpty() }
                        .firstNotNullOfOrNull { line -> line.takeIf { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }?.substringAfter(':')?.trim() }
                val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest("${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11".toByteArray()))
                socket.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                input.readText()
            }
        }

        override fun close() {
            server.close()
            synchronized(sockets) { sockets.forEach { runCatching { it.close() } } }
        }
    }

    /** relay_errors is keyed by the normalized URL amy queried. */
    private fun String.normalized() = normalizeRelayUrl().url

    private companion object {
        const val REASON = "filters are too vague"
    }
}
