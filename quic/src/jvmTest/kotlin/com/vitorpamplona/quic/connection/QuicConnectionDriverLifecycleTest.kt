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

import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import com.vitorpamplona.quic.transport.UdpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the driver close-path cleanup contract for soak target #6:
 * "Resource cleanup at session close. Run a session, close it, run
 * another, repeat 100×. Verify no thread leaks ([Thread.getAllStackTraces]
 * count stable), no socket leaks (netstat count stable), no
 * Dispatchers.IO worker buildup."
 *
 * The driver spawns three things that need clean teardown:
 *  - A [kotlinx.coroutines.SupervisorJob] parented to the test's scope
 *    (driver.driverJob).
 *  - Two child coroutines (read loop + send loop) launched on
 *    `parentScope.coroutineContext + job + Dispatchers.IO`.
 *  - A [com.vitorpamplona.quic.transport.UdpSocket] bound to an
 *    ephemeral OS port.
 *
 * If any of these leaks across [QuicConnectionDriver.close], a 3-hour
 * audio-room session that the user joins+leaves repeatedly (typical
 * UX: switch rooms a few times, the OS swaps networks, etc.) eventually
 * exhausts file descriptors or blooms the JVM thread pool.
 *
 * The fixture uses a localhost UDP "blackhole" — a DatagramSocket bound
 * to an ephemeral port that does NOT speak QUIC. The client driver
 * tries to handshake but never succeeds; the test isn't about
 * handshake correctness, it's about what happens to the driver's
 * resources when the application aborts the session before connect.
 *
 * Acceptance bands (intentionally generous to avoid CI flakiness):
 *  - Per-session: connection.status latches CLOSED inside close()'s
 *    bounded wait, driver.driverJob.isCompleted == true after we
 *    cancel the parent scope, and a second close() call is a no-op
 *    (idempotency contract added in round-5 #5).
 *  - Across 100 sessions: net thread growth < 16, where the typical
 *    Dispatchers.IO worker pool fluctuation is 4–8 on this JVM. A
 *    leak that creates one persistent thread per session would
 *    produce ~100 net growth; 16 is two orders of magnitude below
 *    that and well above ambient JVM noise.
 */
class QuicConnectionDriverLifecycleTest {
    private val blackhole: DatagramSocket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))

    @AfterTest
    fun tearDown() {
        runCatching { blackhole.close() }
    }

    @Test
    fun closeIsIdempotentAndDriverJobCompletes() =
        runBlocking {
            val parent = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val (driver, connection, socket) = startDriver(parent)

            // Let the driver kick off a few PTO cycles so there's actual
            // in-flight crypto when close() runs — exercise the
            // close-flush path that actually has work to flush.
            delay(50)

            driver.close()
            // close() launches a teardown coroutine on parentScope and
            // returns immediately. Wait for that coroutine to finish.
            withTimeoutOrNull(5_000L) { driver.closeTeardownJob?.join() }
                ?: error("close teardown coroutine never completed within 5 s — driver leaked")

            assertEquals(
                QuicConnection.Status.CLOSED,
                connection.status,
                "connection.status must be CLOSED after the close-teardown coroutine joins",
            )
            assertTrue(
                socketIsClosed(socket),
                "socket.close() must have run by the time the teardown coroutine completes",
            )

            // Idempotency: a second close() must be a no-op (round-5 #5
            // memoizes the teardown launch). It must not throw, must
            // not relaunch teardown, must not produce a new
            // closeTeardownJob — the original Job stays.
            val originalTeardown = driver.closeTeardownJob
            driver.close()
            assertTrue(
                driver.closeTeardownJob === originalTeardown,
                "second close() must reuse the memoized teardown Job (round-5 #5 idempotency)",
            )

            // Cancel the parent so SupervisorJob children unwind. Then
            // assert the driver's job is fully done.
            parent.cancel()
            withTimeoutOrNull(5_000L) { driver.driverJob.join() }
            assertTrue(
                driver.driverJob.isCompleted,
                "driver.driverJob must be completed after parent.cancel() — observed isCompleted=" +
                    "${driver.driverJob.isCompleted} active=${driver.driverJob.isActive}",
            )
        }

    @Test
    fun repeatedSessionLifecycleDoesNotLeakThreads() =
        runBlocking {
            // Warm up Dispatchers.IO so its worker count has stabilised
            // before we sample. Otherwise the first session's growth
            // (creating fresh IO workers from the pool's lazy init)
            // shows up as a leak.
            warmUpDispatchersIo()
            val baseline = liveThreadCount()
            val baselineFds = liveFileDescriptorCount()
            val sessions = 100
            for (i in 0 until sessions) {
                val parent = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val (driver, connection, socket) = startDriver(parent)
                // Keep the session very short — just long enough that
                // the driver kicked off both read + send loops and may
                // have done a PTO retransmit.
                delay(10)
                driver.close()
                withTimeoutOrNull(2_000L) { driver.closeTeardownJob?.join() }
                    ?: error(
                        "session $i: close teardown coroutine never completed — driver leaked. " +
                            "Status=${connection.status}",
                    )
                parent.cancel()
                withTimeoutOrNull(2_000L) { driver.driverJob.join() }
                    ?: error("session $i: driver job did not complete after parent.cancel()")
                assertEquals(
                    QuicConnection.Status.CLOSED,
                    connection.status,
                    "session $i: connection must be CLOSED after teardown",
                )
                assertTrue(
                    socketIsClosed(socket),
                    "session $i: UDP socket must be closed",
                )
            }

            // Coroutines may still be wrapping up; give Dispatchers.IO
            // a small drain window so any in-flight worker reuse
            // settles before we sample.
            delay(200)
            val finalCount = liveThreadCount()
            val growth = finalCount - baseline
            // Generous band — the IO pool naturally fluctuates a few
            // workers. A real leak (one persistent thread per session)
            // would push growth ≥ ~100. 16 is strictly above the
            // observed JVM noise band and well below leak.
            assertTrue(
                growth <= 16,
                "thread count grew by $growth across $sessions sessions " +
                    "(baseline=$baseline final=$finalCount). Anything > 16 indicates a leak.",
            )

            // FD-leak canary. On Linux, /proc/self/fd holds one entry per
            // open file descriptor (sockets + pipes + regular files).
            // A leak that misses socket.close() would show up here as
            // ~1 FD per session — 100 sessions → growth ≥ 100 with
            // certainty. We band at 16 (same headroom as threads). On
            // platforms without /proc, [liveFileDescriptorCount] returns
            // -1 and this branch silently no-ops.
            val finalFds = liveFileDescriptorCount()
            if (baselineFds >= 0 && finalFds >= 0) {
                val fdGrowth = finalFds - baselineFds
                assertTrue(
                    fdGrowth <= 16,
                    "FD count grew by $fdGrowth across $sessions sessions " +
                        "(baseline=$baselineFds final=$finalFds). UDP socket / pipe leak.",
                )
            }
        }

    @Test
    fun socketDeathMidSessionFlipsConnectionToClosed() =
        runBlocking {
            // Soak target #3: when Android backgrounds the app and the
            // OS reclaims the UDP socket FD ~30 s later, the next
            // `socket.send` from the QUIC driver throws. Without the
            // try/catch on the send loop, that throw escapes silently
            // into the SupervisorJob and the connection sits in
            // HANDSHAKING / CONNECTED forever — the
            // ReconnectingNestsListener's terminal-state listener
            // never fires, the room screen shows "live" while audio
            // is dead, and the user has to back out and rejoin.
            //
            // Pin the contract: closing the socket out from under a
            // running driver MUST flip connection.status to CLOSED
            // within a bounded window (the next send-loop iteration —
            // typically the next PTO firing). The reconnect orchestrator
            // observes this as terminal and reschedules a fresh handshake.
            val parent = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val (driver, connection, socket) = startDriver(parent)

            // Let the driver get past start() and into the read+send
            // loop steady state — it doesn't matter that the handshake
            // isn't completing (no peer); the send loop is alive and
            // will hit the dead socket on its next drain or PTO.
            delay(50)
            // The driver's status here is HANDSHAKING (no peer to
            // complete it). Verify that's our pre-condition rather
            // than CLOSED — otherwise the test would trivially pass.
            assertTrue(
                connection.status != QuicConnection.Status.CLOSED,
                "pre-condition: connection must NOT yet be CLOSED (was ${connection.status})",
            )

            // Simulate the OS killing the socket. UdpSocket.close()
            // is the same path Android takes when the kernel reclaims
            // a backgrounded app's FDs.
            socket.close()

            // Wait for the send loop to actually attempt a send and
            // raise. The send loop fires every PTO; the first PTO is
            // ~1 s for a connection without an RTT sample. Give it 3 s
            // of headroom.
            val startedAt = System.nanoTime()
            while (connection.status != QuicConnection.Status.CLOSED &&
                (System.nanoTime() - startedAt) < 5_000_000_000L // 5 s in nanos
            ) {
                delay(50)
            }
            assertEquals(
                QuicConnection.Status.CLOSED,
                connection.status,
                "connection must transition to CLOSED after socket dies; pre-fix it would " +
                    "stay HANDSHAKING/CONNECTED indefinitely because the send-loop throw " +
                    "escaped the SupervisorJob silently",
            )
            // closeReason should be populated for observability — the
            // ReconnectingNestsListener orchestrator surfaces this in
            // its NestsListenerState.Failed.reason. Either the read
            // loop's finally (socket.receive returns null on close) or
            // the send loop's catch (socket.send throws on closed
            // socket) gets there first depending on scheduler timing;
            // both produce a human-readable message that mentions the
            // loop. The exact path is racy, so we just check both
            // possible reason strings cover the symptom.
            val reason = connection.closeReason
            assertTrue(
                reason != null && (reason.contains("read loop") || reason.contains("send loop")),
                "closeReason must mention the loop death so observability surfaces the cause; " +
                    "got '$reason'",
            )

            // Cleanup — the driver's job tree should still wind down
            // cleanly even though we tore the socket out from under it.
            driver.close()
            withTimeoutOrNull(2_000L) { driver.closeTeardownJob?.join() }
            parent.cancel()
            withTimeoutOrNull(2_000L) { driver.driverJob.join() }
            assertTrue(
                driver.driverJob.isCompleted,
                "driver job must complete cleanly even after a socket-death teardown",
            )
        }

    /** True if a UDP send on the socket throws — i.e. close() has run. */
    private fun socketIsClosed(socket: UdpSocket): Boolean =
        try {
            runBlocking { socket.send(ByteArray(1)) }
            false
        } catch (_: Throwable) {
            true
        }

    private suspend fun startDriver(parent: CoroutineScope): Triple<QuicConnectionDriver, QuicConnection, UdpSocket> {
        val socket = UdpSocket.connect("127.0.0.1", blackhole.localPort)
        val connection =
            QuicConnection(
                serverName = "lifecycle.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val driver = QuicConnectionDriver(connection, socket, parent)
        driver.start()
        return Triple(driver, connection, socket)
    }

    private suspend fun warmUpDispatchersIo() {
        // Touch Dispatchers.IO from a few coroutines so the lazy worker
        // pool has its baseline workers spun up. Without this the
        // first measurement's "baseline" is artificially low and the
        // first sessions' creation of IO workers looks like a leak.
        repeat(4) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { Thread.sleep(2) }
        }
    }

    private fun liveThreadCount(): Int = Thread.getAllStackTraces().keys.size

    /**
     * Linux: count entries in `/proc/self/fd`. macOS / Windows / other
     * platforms don't expose this path; return -1 so the caller
     * silently skips the FD assertion. This is a strictly additive
     * canary — no false-positive risk on platforms that can't measure.
     */
    private fun liveFileDescriptorCount(): Int =
        try {
            val procFd = File("/proc/self/fd")
            if (procFd.isDirectory) procFd.list()?.size ?: -1 else -1
        } catch (_: Throwable) {
            -1
        }
}
