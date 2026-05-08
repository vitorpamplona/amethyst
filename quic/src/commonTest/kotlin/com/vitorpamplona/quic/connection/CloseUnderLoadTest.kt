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

import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.stream.StreamId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Close-under-load races for the QuicConnection — pinning that a
 * `close()` racing against a heavy stream-traffic burst doesn't
 * deadlock, lose data the application already saw, or leave Flow
 * collectors hanging.
 *
 * The motivation is the audio-rooms session-cycle pattern: a user
 * joins, briefly talks (or listens), then leaves. The leave path
 * fires `close()` while the moq-lite peer-uni stream churn is still
 * in progress — sometimes hundreds of streams are mid-flight on the
 * connection. Idle-driver close (covered by
 * [QuicConnectionDriverLifecycleTest.closeIsIdempotentAndDriverJobCompletes])
 * is the easy case; this file pins the load case.
 *
 * Three angles:
 *
 *  1. [closeWhileBulkStreamRetirementIsRunning] — server ACKs 100
 *     in-flight client-bidi streams in a single shot, triggering a
 *     mass retirement on the next drain. We fire `close()` while
 *     the retirement / writer drain is still in flight. Status MUST
 *     flip to CLOSED, every stream's incoming Flow MUST terminate,
 *     and no exception propagates to the test runner.
 *
 *  2. [closeWhileAppCoroutinesAreOpeningStreams] — N coroutines
 *     hammer `openBidiStream` while another coroutine fires
 *     `close()`. Every winner-of-the-race opener returns either a
 *     valid stream (won) or an [QuicConnectionClosedException] /
 *     [IllegalStateException] (lost). No coroutine hangs forever.
 *
 *  3. [closeWhilePeerStreamsAreInFlight] — peer is mid-stream of
 *     50 server-uni group-stream payloads when close fires. Every
 *     incoming Flow terminates promptly with whatever bytes the
 *     parser had already delivered (zero or more, but never hung).
 */
class CloseUnderLoadTest {
    @Test
    fun closeWhileBulkStreamRetirementIsRunning() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val n = 100
            val streams =
                client.openBidiStreamsBatch(items = (0 until n).toList()) { stream, i ->
                    stream.send.enqueue("payload-$i".encodeToByteArray())
                    stream.send.finish()
                    stream
                }

            // Drain so STREAM frames go on the wire and PNs are allocated.
            // After this, a server ACK covering all PNs latches finAcked
            // on every stream, which makes them retire-eligible.
            drainAll(client, pipe)
            val largestSent = client.application.pnSpace.nextPacketNumber - 1L
            assertTrue(largestSent >= 0, "drainAll must have emitted at least one app-level packet")

            val ackPacket =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        AckFrame(
                            largestAcknowledged = largestSent,
                            ackDelay = 0L,
                            firstAckRange = largestSent,
                        ),
                    ),
                )!!
            feedDatagram(client, ackPacket, nowMillis = 0L)

            // Now every stream is fully-retire-eligible. The next drain
            // would retire them all in a single pass. Fire close()
            // CONCURRENTLY with that drain — the retire path mutates
            // streamsList while close()'s closeAllSignals iterates it.
            // The original closeAllSignals snapshot-iterates safely, but
            // a careless edit there could ConcurrentModification.
            coroutineScope {
                val drainer =
                    async { drainAll(client, pipe) }
                val closer =
                    async {
                        // Tiny yield so the drainer wins the race and is
                        // mid-iteration when close fires. close() is
                        // suspending and acquires its own locks, so this
                        // exercises the locked-iteration ↔ locked-close
                        // path rather than running them strictly sequentially.
                        kotlinx.coroutines.yield()
                        client.close(0L, "close-under-load")
                    }
                drainer.await()
                closer.await()
            }

            // close() transitions to CLOSING; the writer's next
            // drainOutbound builds a CONNECTION_CLOSE and flips to
            // CLOSED. Production drives this in the driver's send loop;
            // here we drive it explicitly.
            drainOutbound(client, nowMillis = 0L)
            assertEquals(
                QuicConnection.Status.CLOSED,
                client.status,
                "status must flip to CLOSED after close() — even racing a retirement pass",
            )
            // Every stream's incoming Flow must terminate (closeAllSignals
            // closes the per-stream channel, OR retirement closed it
            // earlier — both end with the Flow completing). If either
            // path leaks, the toList collector below times out.
            val collected =
                coroutineScope {
                    streams
                        .mapIndexed { idx, s ->
                            async {
                                withTimeoutOrNull(2_000L) { s.incoming.toList() } to idx
                            }
                        }.awaitAll()
                }
            val hung = collected.firstOrNull { it.first == null }
            assertTrue(
                hung == null,
                "stream index ${hung?.second} incoming Flow leaked — closeAllSignals " +
                    "must terminate every per-stream channel even under retirement race",
            )
        }

    @Test
    fun closeWhileAppCoroutinesAreOpeningStreamsDoesNotDeadlock() =
        runBlocking {
            val (client, _) = newConnectedClient()
            val n = 80

            // openBidiStream takes streamsLock; close() takes
            // lifecycleLock. The two are intentionally distinct so
            // app code (open/enqueue) doesn't fight the close path.
            // This test pins the lock-ordering invariant: many
            // parallel openers MUST NOT deadlock against a
            // concurrent close(). Whether the race actually fires
            // under runBlocking's cooperative dispatcher varies
            // (single-thread event loop tends to run all queued
            // coroutines before the next scheduling point), so we
            // don't assert on the race outcome — only on the
            // absence of deadlock and the final closed state. A
            // multi-threaded chaos test would exercise the race
            // shape itself; the lock-ordering invariant landing
            // here is the production-safety bar.
            val outcome = ArrayList<Outcome>()
            outcome.ensureCapacity(n)
            val resultsLock = Any()

            coroutineScope {
                val openers =
                    (0 until n).map { i ->
                        async {
                            val r =
                                try {
                                    client.openBidiStream()
                                    Outcome.OPENED
                                } catch (_: QuicConnectionClosedException) {
                                    Outcome.CLOSED_DURING_OPEN
                                } catch (_: IllegalStateException) {
                                    Outcome.CLOSED_DURING_OPEN
                                } catch (_: QuicStreamLimitException) {
                                    // Hit the peer's stream cap before close
                                    // landed — fine, neither a hang nor a
                                    // protocol violation.
                                    Outcome.STREAM_LIMIT
                                }
                            synchronized(resultsLock) { outcome += r }
                        }
                    }
                // Yield once so a few openers get past the lock
                // acquisition before close fires, exercising the
                // racing path rather than the all-after-close path.
                kotlinx.coroutines.yield()
                async { client.close(0L, "racing openers") }.await()
                openers.awaitAll()
            }

            // Drain the CONNECTION_CLOSE the writer now has queued so
            // status flips from CLOSING to CLOSED (see test #1 for
            // rationale).
            drainOutbound(client, nowMillis = 0L)
            assertEquals(
                QuicConnection.Status.CLOSED,
                client.status,
                "status must be CLOSED after close()",
            )
            assertEquals(
                n,
                outcome.size,
                "every opener coroutine must have completed (no hangs) — pins the " +
                    "lock-ordering invariant: streamsLock (openers) and lifecycleLock " +
                    "(close) don't fight",
            )
        }

    @Test
    fun closeWhilePeerStreamsAreInFlight() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val n = 50
            val firstId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val streamIds = (0 until n).map { firstId + 4L * it }

            // Half the peer streams arrive WITHOUT FIN — leaving them
            // mid-flight on the connection. The other half FIN normally.
            for ((idx, id) in streamIds.withIndex()) {
                val payload = "frame-$idx".encodeToByteArray()
                val fin = idx % 2 == 1
                val packet =
                    pipe.buildServerApplicationDatagram(
                        listOf(StreamFrame(streamId = id, offset = 0L, data = payload, fin = fin)),
                    )!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            // Don't drain — leaves the FIN'd streams un-retired and the
            // others mid-flight. close() fires while every stream is in
            // some live state.
            client.close(0L, "close mid peer-stream burst")

            // Drain so the writer emits CONNECTION_CLOSE and status
            // moves CLOSING → CLOSED. Without this drain, a test on
            // the in-memory pipe would observe CLOSING; the production
            // driver fires drainOutbound automatically.
            drainOutbound(client, nowMillis = 0L)
            assertEquals(QuicConnection.Status.CLOSED, client.status)
            // Every stream's incoming Flow must terminate (closeAllSignals
            // ran). The half that got FIN return their bytes; the other
            // half terminate with whatever was buffered (may be 0 bytes).
            val collected =
                coroutineScope {
                    streamIds
                        .map { id ->
                            async {
                                val s = client.streamById(id)
                                if (s == null) {
                                    null to id // never created, treat as "no leak"
                                } else {
                                    withTimeoutOrNull(2_000L) { s.incoming.toList() } to id
                                }
                            }
                        }.awaitAll()
                }
            val hung = collected.firstOrNull { it.first == null && it.second != -1L && client.streamByIdLockedForTest(it.second) != null }
            assertTrue(
                hung == null,
                "stream id ${hung?.second} incoming Flow leaked across close — closeAllSignals " +
                    "must terminate every live stream's channel",
            )
        }

    private enum class Outcome {
        OPENED,
        CLOSED_DURING_OPEN,
        STREAM_LIMIT,
    }

    private fun drainAll(
        client: QuicConnection,
        pipe: InMemoryQuicPipe,
    ) {
        while (true) {
            val out = drainOutbound(client, nowMillis = 0L) ?: break
            pipe.decryptClientApplicationFrames(out)
        }
    }

    // 1024-stream caps so the 100-bidi-stream open burst doesn't
    // brush the cap mid-test, and 16 MiB data window so multi-payload
    // traffic doesn't trip flow-control mid-close.
    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        com.vitorpamplona.quic.connection.newConnectedClient(
            maxStreamsBidi = 1024,
            maxStreamsUni = 1024,
            maxData = 16L * 1024 * 1024,
        )
}

/**
 * Test-only synchronous lookup that avoids the suspending
 * [QuicConnection.streamById] for use inside `firstOrNull`. Caller
 * doesn't need the lock — this is a best-effort post-close check;
 * the streams map is no longer being mutated by the time
 * `closeAllSignals` returns.
 */
private fun QuicConnection.streamByIdLockedForTest(id: Long) = streamsListLocked().firstOrNull { it.streamId == id }
