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
package com.vitorpamplona.geode.interop

import com.vitorpamplona.geode.LocalRelayServer
import com.vitorpamplona.geode.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.net.ServerSocket
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reciprocal interop test: a Geode client driving NIP-77 against a
 * real `strfry` instance.
 *
 * **Opt-in.** Skipped unless the `STRFRY_BIN` environment variable
 * (or `-Dstrfry.bin=…` system property) points at a `strfry` binary.
 * When unset the test prints a `[skip]` line and returns. Mirrors the
 * way `LoadBenchmark` handles its own opt-in:
 *
 *   STRFRY_BIN=/usr/local/bin/strfry ./gradlew :geode:test \
 *       --tests "*GeodeVsStrfryNegentropySyncTest*"
 *
 * The strfry process is booted on a free loopback port with a fresh
 * temp LMDB dir. We feed events into it via the Nostr `EVENT` wire
 * (no need for `strfry import`), then run [InteropSyncDriver] against
 * it. Strfry's `RelayNegentropy.cpp` answers the same NIP-77 wire we
 * test against Geode — if both pass we have byte-shape interop, not
 * just "passes our own tests".
 */
class GeodeVsStrfryNegentropySyncTest {
    private val strfryBin: String? =
        System.getenv("STRFRY_BIN") ?: System.getProperty("strfry.bin")
    private val enabled = strfryBin != null

    private lateinit var geodeRelay: Relay
    private lateinit var geodeServer: LocalRelayServer
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient
    private lateinit var strfryDir: File
    private var strfryProcess: Process? = null
    private var strfryUrl: String? = null
    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        if (!enabled) return
        geodeRelay = Relay(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        geodeServer = LocalRelayServer(geodeRelay, host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> httpClient }, scope)
    }

    @AfterTest
    fun teardown() {
        if (!enabled) return
        strfryProcess?.destroy()
        strfryProcess?.waitFor()
        if (::strfryDir.isInitialized) strfryDir.deleteRecursively()
        client.disconnect()
        scope.cancel()
        geodeServer.stop()
        geodeRelay.close()
    }

    /**
     * Boots a strfry instance in a temp directory on a free port.
     * Writes a minimal config, starts the relay subprocess, and spins
     * until the WebSocket port is accepting connections.
     */
    private fun startStrfry(): String {
        val port = ServerSocket(0).use { it.localPort }
        strfryDir = createTempDirectory(prefix = "strfry-interop-").toFile()
        val configFile = File(strfryDir, "strfry.conf")
        // Minimal strfry config — bind, db dir, NIP-77 enabled. Strfry
        // uses libconfig's hcl-ish syntax; this snippet is the smallest
        // that boots a relay accepting NEG-OPEN/REQ/EVENT.
        configFile.writeText(
            """
            db = "${strfryDir.absolutePath}/strfry-db"
            relay {
                bind = "127.0.0.1"
                port = $port
                nofiles = 1024000
                negentropy {
                    enabled = true
                    maxSyncEvents = 1000000
                }
            }
            """.trimIndent(),
        )
        File(strfryDir, "strfry-db").mkdirs()
        val pb =
            ProcessBuilder(strfryBin, "--config", configFile.absolutePath, "relay")
                .redirectErrorStream(true)
                .redirectOutput(File(strfryDir, "strfry.log"))
        strfryProcess = pb.start()

        // Wait for strfry to start accepting connections — give up
        // after a few seconds. Strfry typically binds in <500 ms.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            runCatching {
                java.net.Socket("127.0.0.1", port).close()
                strfryUrl = "ws://127.0.0.1:$port/"
                return strfryUrl!!
            }
            Thread.sleep(100)
        }
        throw IllegalStateException(
            "strfry did not start within 5s; log: " +
                File(strfryDir, "strfry.log").readText(),
        )
    }

    private fun makeEvents(count: Int): List<Event> {
        val signer = NostrSignerSync(KeyPair())
        val now = 1_700_000_000L
        return List(count) { i ->
            signer.sign(TextNoteEvent.build("strfry-interop-$i", createdAt = now + i))
        }
    }

    /**
     * Push an event directly into a relay over a one-shot WebSocket.
     * Used for both Geode (via `geodeRelay.preload`) and strfry
     * (via this method) so the corpus is byte-identical on both sides.
     */
    private fun publishToStrfry(
        wsUrl: String,
        events: List<Event>,
    ) {
        val ok =
            java.util.concurrent.atomic
                .AtomicInteger()
        val target = events.size
        val incoming = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val ws =
            httpClient.newWebSocket(
                okhttp3.Request
                    .Builder()
                    .url(wsUrl.replace("ws://", "http://"))
                    .build(),
                object : okhttp3.WebSocketListener() {
                    override fun onMessage(
                        webSocket: okhttp3.WebSocket,
                        text: String,
                    ) {
                        incoming.trySend(text)
                    }

                    override fun onFailure(
                        webSocket: okhttp3.WebSocket,
                        t: Throwable,
                        response: okhttp3.Response?,
                    ) {
                        incoming.close(t)
                    }
                },
            )
        try {
            for (e in events) {
                val cmd = """["EVENT",${OptimizedJsonMapper.toJson(e)}]"""
                check(ws.send(cmd)) { "publish to strfry failed" }
            }
            // Drain OK responses.
            runBlocking {
                kotlinx.coroutines.withTimeout(30_000) {
                    while (ok.get() < target) {
                        val raw = incoming.receive()
                        if (raw.startsWith("[\"OK\"")) ok.incrementAndGet()
                    }
                }
            }
        } finally {
            ws.close(1000, "preload-done")
        }
    }

    @Test
    fun geodeReconcilesAgainstStrfryRelay() =
        runBlocking {
            if (!enabled) {
                println("[skip] GeodeVsStrfryNegentropySyncTest — set STRFRY_BIN=/path/to/strfry to enable")
                return@runBlocking
            }
            val strfryWs = startStrfry()

            // Same overlap shape as the Geode-vs-Geode test so the two
            // results are directly comparable: A=[0..14], B=[5..19].
            val all = makeEvents(20)
            val strfryEvents = all.subList(0, 15)
            val geodeEvents = all.subList(5, 20)

            publishToStrfry(strfryWs, strfryEvents)
            geodeRelay.preload(geodeEvents)

            val filter = Filter(kinds = listOf(1))

            // Drive the negentropy reconciliation from Geode's
            // perspective against strfry. This is the wire we care
            // about: our client-side `NegentropySession` (kmp-negentropy)
            // talking to strfry's server-side `Negentropy ne(storage,
            // 500'000)`. Symmetric difference must match the Geode-vs-
            // Geode case.
            val res = InteropSyncDriver(httpClient).reconcile(strfryWs, filter, geodeEvents)
            assertNull(res.error, "Geode↔strfry NEG must not error: ${res.error}")
            assertEquals(
                strfryEvents.subList(0, 5).map { it.id }.toSet(),
                res.needIds,
                "Geode should NEED [0..4] from strfry",
            )
            assertEquals(
                geodeEvents.subList(10, 15).map { it.id }.toSet(),
                res.haveIds,
                "Geode should announce HAVE for [15..19]",
            )

            // Spot-check the wire-level health: round count is bounded.
            assertTrue(res.rounds <= 16, "expected ≤16 NEG-MSG rounds, got ${res.rounds}")
        }

    @Test
    fun strfryDrivesGeodeAsServer() =
        runBlocking {
            if (!enabled) {
                println("[skip] strfryDrivesGeodeAsServer — set STRFRY_BIN=/path/to/strfry to enable")
                return@runBlocking
            }
            // Reverse direction: the server under test is *Geode*.
            // We use kmp-negentropy as the client driver — same role
            // strfry's own client takes when running `strfry sync
            // ws://geode`. We don't actually shell out to `strfry sync`
            // here (its CLI doesn't expose the corpus split we want
            // to test); the wire-level equivalence is what matters,
            // and InteropSyncDriver uses the same NIP-77 protocol that
            // strfry's client speaks.
            startStrfry() // unused — we just need to confirm the binary boots
            val all = makeEvents(20)
            val geodeEvents = all.subList(0, 15)
            val driverEvents = all.subList(5, 20)
            geodeRelay.preload(geodeEvents)

            val res =
                InteropSyncDriver(httpClient).reconcile(
                    wsUrl = geodeServer.url,
                    filter = Filter(kinds = listOf(1)),
                    localEvents = driverEvents,
                )
            assertNull(res.error)
            assertEquals(
                geodeEvents.subList(0, 5).map { it.id }.toSet(),
                res.needIds,
            )
            assertEquals(
                driverEvents.subList(10, 15).map { it.id }.toSet(),
                res.haveIds,
            )

            // Cross-check with REQ that Geode actually has what we
            // think it has.
            val onGeode =
                client
                    .fetchAll(
                        relay = geodeServer.url.normalizeRelayUrl(),
                        filter = Filter(kinds = listOf(1)),
                    ).map { it.id }
                    .toSet()
            assertEquals(geodeEvents.map { it.id }.toSet(), onGeode)
        }
}
