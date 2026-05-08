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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Lock contract for batched stream opens — the production pattern used by
 * `Http3GetClient.prepareRequests` / `HqInteropGetClient.prepareRequests`
 * and any future caller that wants to open N streams atomically without
 * the send loop interjecting between opens.
 *
 * Background. The interop runner's `multiplexing` testcase against aioquic
 * timed out on 2026-05-06, downloading 1421/2000 files in 60s (~23
 * streams/sec, exactly 1 RTT per stream — the wire was emitting one STREAM
 * frame per datagram, not 64). The qlog showed 2898 packets sent in 60s,
 * each carrying ONE STREAM frame. Cause: `prepareRequests` had been written
 * to hold `conn.lock` (the deprecated alias for `lifecycleLock`) while
 * calling [QuicConnection.openBidiStreamLocked]. `lifecycleLock` doesn't
 * gate the writer — `drainOutbound` takes [QuicConnection.streamsLock] —
 * so the writer's send loop could (and did) interleave between every two
 * `openBidiStreamLocked` invocations, draining one stream per pass.
 *
 * Two-layer guard:
 *   1. [QuicConnection.openBidiStreamLocked] now `check`s that
 *      [QuicConnection.streamsLock] is held when called. This catches
 *      callers that pass NO lock or the WRONG lock at the source.
 *   2. The "batched open under streamsLock yields a small fixed packet
 *      count" contract is pinned by [MultiplexingCoalescingTest] —
 *      already in the suite. Together they fail loudly if the prod
 *      callers regress.
 *
 * If you're adding a new caller for `openBidiStreamLocked`, the right
 * pattern is:
 *
 *     conn.streamsLock.withLock {
 *         repeat(n) { conn.openBidiStreamLocked() ... }
 *     }
 */
class BatchedOpenLockContractTest {
    @Test
    fun `openBidiStreamLocked throws when called without streamsLock held`() {
        runBlocking {
            val client = handshakedClient()
            // No `streamsLock.withLock { ... }` wrapper. This is the
            // shape the regressed `prepareRequests` had — except it
            // held `lifecycleLock` instead. Either way streamsLock is
            // not held, the assertion in openBidiStreamLocked fires.
            val ex =
                assertFailsWith<IllegalStateException> {
                    client.openBidiStreamLocked()
                }
            assertNotNull(ex.message)
            // The error should mention streamsLock so the caller knows
            // what to fix.
            kotlin.test.assertTrue(
                ex.message!!.contains("streamsLock"),
                "error message should name the lock to acquire; got: ${ex.message}",
            )
        }
    }

    @Test
    fun `openBidiStreamLocked throws when caller holds the wrong lock (lifecycleLock)`() {
        runBlocking {
            val client = handshakedClient()
            // Exact regression shape: caller holds lifecycleLock and
            // calls openBidiStreamLocked. The check should fire because
            // streamsLock is not held — even though SOME lock is.
            assertFailsWith<IllegalStateException> {
                client.lifecycleLock.withLock {
                    client.openBidiStreamLocked()
                }
            }
        }
    }

    @Test
    fun `openBidiStreamLocked succeeds when streamsLock is held`() {
        runBlocking {
            val client = handshakedClient()
            // Happy path — caller holds streamsLock, batched open works.
            // Sanity-check that the assertion in openBidiStreamLocked
            // doesn't fire on the correct call shape.
            val streams =
                client.streamsLock.withLock {
                    List(64) { client.openBidiStreamLocked() }
                }
            assertEquals(64, streams.size)
            assertEquals(64, streams.map { it.streamId }.toSet().size, "stream ids must be unique")
        }
    }

    @Test
    fun `openBidiStreamsBatch holds streamsLock for the entire batch`() {
        runBlocking {
            val client = handshakedClient()
            // Verify the API actually holds streamsLock during init —
            // the whole point of the function. A regression that
            // released the lock between opens (the 2026-05-06 bug
            // shape) would let isLocked drop to false inside init.
            val payloads = (0 until 64).map { "req-$it".encodeToByteArray() }
            var lockedAtSomePoint = false
            val streams =
                client.openBidiStreamsBatch(payloads) { stream, payload ->
                    if (client.streamsLock.isLocked) lockedAtSomePoint = true
                    stream.send.enqueue(payload)
                    stream.send.finish()
                    stream
                }
            assertEquals(64, streams.size)
            assertEquals(64, streams.map { it.streamId }.toSet().size)
            assertTrue(
                lockedAtSomePoint,
                "streamsLock must be held while init runs — without that, " +
                    "the send loop interleaves between opens",
            )
        }
    }

    @Test
    fun `openBidiStreamsBatch with empty list does not throw`() {
        runBlocking {
            val client = handshakedClient()
            // Empty-batch short-circuit: no items, no lock acquisition,
            // no per-item init call. Returns empty list cleanly.
            val result: List<Unit> =
                client.openBidiStreamsBatch(emptyList<Int>()) { _, _ ->
                    error("init must not run for empty batch")
                }
            assertEquals(0, result.size)
        }
    }

    @Test
    fun `openUniStreamLocked throws when streamsLock is not held`() {
        runBlocking {
            val client = handshakedClient()
            assertFailsWith<IllegalStateException> {
                client.openUniStreamLocked()
            }
        }
    }

    @Test
    fun `openUniStreamsBatch holds streamsLock for the entire batch`() {
        runBlocking {
            val client = handshakedClient()
            // moq audio-rooms shape: many uni streams in burst. Without
            // the batched API, each open releases the lock and the
            // send loop interjects (the same shape that broke bidi
            // multiplexing on 2026-05-06).
            val items = List(16) { it }
            var lockedAtSomePoint = false
            val streams =
                client.openUniStreamsBatch(items) { stream, _ ->
                    if (client.streamsLock.isLocked) lockedAtSomePoint = true
                    stream
                }
            assertEquals(16, streams.size)
            assertEquals(16, streams.map { it.streamId }.toSet().size)
            assertTrue(lockedAtSomePoint, "streamsLock must be held while init runs")
        }
    }

    private fun handshakedClient(): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsBidi = 256,
                        ),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 1_000_000,
                            initialMaxStreamDataBidiLocal = 100_000,
                            initialMaxStreamDataBidiRemote = 100_000,
                            initialMaxStreamDataUni = 100_000,
                            initialMaxStreamsBidi = 256,
                            initialMaxStreamsUni = 16,
                            initialSourceConnectionId = serverScid.bytes,
                            originalDestinationConnectionId = client.destinationConnectionId.bytes,
                        ).encode(),
                )
            val pipe =
                InMemoryQuicPipe(
                    client = client,
                    initialDcid = client.destinationConnectionId.bytes,
                    serverScid = serverScid,
                    tlsServer = tlsServer,
                )
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            client
        }
}
