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
package com.vitorpamplona.quic.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM/Android UDP socket using a blocking [DatagramChannel]. We don't use NIO
 * selectors because each QUIC connection has exactly one socket and one receive
 * loop — a Selector doesn't pay for itself at this scale.
 *
 * Threading: the blocking `recvfrom` parks its thread for the *entire* life of
 * the connection (it only returns when a datagram arrives or the socket
 * closes). If that ran on the shared [Dispatchers.IO] pool it would pin one
 * pool thread per connection, and past ~64 concurrent connections it would
 * starve *all* other `Dispatchers.IO` work in the process — this module's and
 * the host app's alike. So each socket owns two dedicated daemon threads:
 * [recvDispatcher] for the perpetually-blocked receive, and [sendDispatcher]
 * for the (rarely-blocking, but still-blocking) send. The receive can't share a
 * thread with send — it would monopolise it — hence two. Both are shut down in
 * [close]. QUIC's blocking socket I/O therefore never touches the shared pool.
 *
 * [connect] still resolves DNS + binds on [Dispatchers.IO]: that's a one-shot
 * setup cost, not a lifetime parker, so it doesn't need isolation.
 *
 * The receive buffer is sized to typical Ethernet MTU; QUIC packets cap at MTU
 * (~1500 in practice).
 */
actual class UdpSocket private constructor(
    private val channel: DatagramChannel,
    private val remote: InetSocketAddress,
) {
    private val closed = AtomicBoolean(false)

    // Dedicated single-thread executors so the blocking socket calls never
    // occupy the shared Dispatchers.IO pool. Daemon threads so a leaked socket
    // can't keep the JVM alive. Separate recv/send threads because the receive
    // parks continuously and would otherwise block sends behind it. We keep the
    // ExecutorService handles (not just the dispatchers) so close() can call
    // shutdownNow() — an interrupt that breaks the parked recvfrom immediately
    // (ClosedByInterruptException) rather than the graceful shutdown() that
    // dispatcher.close() would do.
    private val recvExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "quic-udp-recv").apply { isDaemon = true } }
    private val sendExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "quic-udp-send").apply { isDaemon = true } }
    private val recvDispatcher: ExecutorCoroutineDispatcher = recvExecutor.asCoroutineDispatcher()
    private val sendDispatcher: ExecutorCoroutineDispatcher = sendExecutor.asCoroutineDispatcher()

    // Sized to typical Ethernet MTU + a bit; QUIC tops out at ~1500 in practice
    // and any larger inbound frame is dropped as malformed anyway. The previous
    // 64 KiB buffer was wasteful per connection.
    private val readBuf = ByteBuffer.allocate(2048)

    /**
     * Lifetime UDP datagram counters. Diagnostic-only. Useful for
     * correlating apparent stream-loss against actual receive-side
     * activity — if [receivedDatagramCount] plateaus while the
     * speaker is still pumping, the loss is on the wire / kernel and
     * not in the QUIC stack. Bytes counter feeds the same diff
     * against
     * [com.vitorpamplona.quic.connection.QuicConnection.flowControlSnapshot]'s
     * `sendConnectionFlowConsumed` for the speaker side.
     */
    private val receivedDatagrams: AtomicLong = AtomicLong(0L)
    private val receivedBytes: AtomicLong = AtomicLong(0L)

    actual val localPort: Int
        get() = (channel.localAddress as InetSocketAddress).port

    actual val receivedDatagramCount: Long
        get() = receivedDatagrams.get()

    actual val receivedByteCount: Long
        get() = receivedBytes.get()

    actual val receiveBufferSizeBytes: Int
        get() = channel.getOption(StandardSocketOptions.SO_RCVBUF)

    actual suspend fun send(payload: ByteArray): Int {
        // Fail fast without dispatching onto a possibly shut-down executor.
        if (closed.get()) throw ClosedChannelException()
        return withContext(sendDispatcher) {
            if (closed.get()) throw ClosedChannelException()
            val buf = ByteBuffer.wrap(payload)
            channel.send(buf, remote)
        }
    }

    actual suspend fun receive(): ByteArray? {
        if (closed.get()) return null
        return withContext(recvDispatcher) {
            if (closed.get()) return@withContext null
            try {
                // No synchronized — only the read loop touches readBuf, by
                // contract. The previous synchronized block was pointless.
                readBuf.clear()
                channel.receive(readBuf) ?: return@withContext null
                readBuf.flip()
                val out = ByteArray(readBuf.remaining())
                readBuf.get(out)
                receivedDatagrams.incrementAndGet()
                receivedBytes.addAndGet(out.size.toLong())
                out
            } catch (_: ClosedChannelException) {
                null
            }
        }
    }

    actual fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                channel.close()
            } catch (_: Throwable) {
                // already closed
            }
            // shutdownNow() interrupts the dedicated workers: a thread parked in
            // a blocking recvfrom throws ClosedByInterruptException (a
            // ClosedChannelException, caught below), so receive() returns null
            // and both threads exit promptly instead of leaking per closed
            // connection. channel.close() above would also unblock it
            // (AsynchronousCloseException), but the interrupt is immediate and
            // guarantees the executor terminates.
            recvExecutor.shutdownNow()
            sendExecutor.shutdownNow()
        }
    }

    actual companion object {
        /**
         * ECT(0) codepoint per RFC 3168 §5: the low 2 bits of the IPv4 TOS
         * byte (or IPv6 Traffic Class byte) set to `10`. Setting this via
         * StandardSocketOptions.IP_TOS marks every outgoing datagram. Note
         * this MASKS into the byte, so we can't accidentally clobber a
         * caller-set DSCP value at this level — we own the socket
         * outright per QUIC connection.
         */
        private const val ECT0_TOS_BITS: Int = 0x02

        actual suspend fun connect(
            host: String,
            port: Int,
        ): UdpSocket =
            withContext(Dispatchers.IO) {
                val address = InetAddress.getByName(host)
                val remote = InetSocketAddress(address, port)
                val channel = DatagramChannel.open()
                channel.configureBlocking(true)
                // Bump SO_RCVBUF before bind so the kernel allocates a
                // generous queue. Default rmem (~200 KB on Linux,
                // similar elsewhere) holds barely 130 ~1500-byte
                // datagrams — which is *exactly* the cap MoQ-over-WT
                // listeners brush against once the relay is fanning
                // out a multi-second broadcast (one peer-uni stream per
                // group, multiple subscribers, occasional reorder /
                // retransmit). Under the burst that follows handshake
                // settle, anything queued past rmem is silently dropped
                // by the kernel, manifesting downstream as
                // "subscription stops mid-broadcast even though
                // publisher.send keeps returning true". 4 MiB gives ~30 s
                // of headroom at sustained 1 KB/frame audio rates.
                runCatching {
                    channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024)
                }
                // Mark every outgoing datagram with ECT(0) (low 2 bits of
                // the IP TOS / Traffic Class byte set to `10` per RFC 3168
                // §5). RFC 9000 §13.4 lets endpoints use ECT(0), ECT(1), or
                // both; ECT(0) is the simplest and what most production
                // QUIC stacks pick. Cheap path-quality signal: routers can
                // mark CE on congestion instead of dropping, the peer
                // reports back via ACK_ECN, and our loss-detection treats
                // CE counts as a congestion event without false-positive
                // retransmits. runCatching because IP_TOS support is
                // platform-dependent — failure leaves us at no-ECN, which
                // is also spec-compliant. The interop runner's `ecn`
                // testcase verifies the pcap shows ECT(0)-marked client
                // packets and ACK_ECN frames in both directions.
                runCatching {
                    channel.setOption(StandardSocketOptions.IP_TOS, ECT0_TOS_BITS)
                }
                channel.bind(InetSocketAddress(0)) // ephemeral
                // RFC 9000 §9 / defence-in-depth: connect() asks the kernel
                // to filter inbound datagrams to those from [remote]. Without
                // this any host on the network can spoof our 4-tuple and
                // force us to attempt AEAD decryption on garbage — each
                // failed packet costs a `Cipher.init` + AAD/decrypt + tag
                // check, and a successful unauthenticated stateless reset
                // forgery would terminate our connection. With connect(),
                // the kernel rejects mismatched-source datagrams before
                // they reach userspace.
                //
                // We connect AFTER bind so the ephemeral local port is
                // chosen first, then the destination is associated. Pure
                // client semantics — `quic` doesn't currently support
                // server-side or connection migration to a new remote IP
                // (path validation rotates DCIDs on the SAME 4-tuple), so
                // pinning the socket to one remote is a strict win.
                channel.connect(remote)
                UdpSocket(channel, remote)
            }
    }
}
