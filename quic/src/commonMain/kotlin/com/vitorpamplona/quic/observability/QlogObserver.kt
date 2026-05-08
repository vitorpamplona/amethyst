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
package com.vitorpamplona.quic.observability

import com.vitorpamplona.quic.connection.EncryptionLevel

/**
 * qlog (draft-marx-qlog) observer interface for QUIC connection events.
 *
 * Tools like qvis (https://qvis.quictools.info/) and Wireshark consume
 * qlog files to render sequence diagrams + RTT graphs + recovery
 * timelines. The on-the-wire format is JSON-NDJSON (one JSON object
 * per line); this interface decouples event emission from format —
 * production code can pass [NoOp] (zero overhead), and the
 * `:quic` interop runner attaches a JSON-writing implementation that
 * produces a `client.sqlog` consumable by qvis.
 *
 * Goal: every interop-runner test failure produces a qlog file the
 * caller can drop into qvis to see exactly what we did differently
 * from the spec.
 *
 * Performance: hooks are on the hot packet-send/receive path, so
 * [NoOp] must be a JIT-able single virtual call with no allocation.
 * Default-method bodies in Kotlin compile to single bytecode `RETURN`,
 * which HotSpot inlines reliably.
 */
interface QlogObserver {
    /** Called once after [com.vitorpamplona.quic.connection.QuicConnection.start] runs. */
    fun onConnectionStarted(
        serverName: String,
        dcid: ByteArray,
        scid: ByteArray,
    )

    /**
     * Called when the connection transitions to CLOSED, either locally
     * (close()) or due to an inbound CONNECTION_CLOSE / read-loop
     * termination ([com.vitorpamplona.quic.connection.QuicConnection.markClosedExternally]).
     *
     * @param initiator `"local"` if we initiated, `"remote"` otherwise.
     */
    fun onConnectionClosed(
        initiator: String,
        errorCode: Long,
        reason: String,
    )

    /**
     * One outbound packet at [level] just hit the wire. Called once per
     * coalesced packet inside a UDP datagram, so a single
     * datagram carrying Initial + Handshake fires twice.
     */
    fun onPacketSent(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    )

    /** One inbound packet at [level] was successfully decrypted + dispatched. */
    fun onPacketReceived(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    )

    /**
     * An inbound packet (or whole datagram) was dropped on the floor —
     * AEAD AUTH FAIL, unknown DCID, missing receive keys, version
     * mismatch, frame decode failure, etc.
     */
    fun onPacketDropped(
        reason: String,
        sizeBytes: Int,
    )

    /**
     * TLS produced new keys at the given encryption level.
     *
     * @param keyType `"server"` or `"client"` (which direction the
     *                key applies to).
     */
    fun onKeyUpdated(
        keyType: String,
        level: EncryptionLevel,
    )

    /**
     * RFC 9002 §6.1 loss detection declared one or more outbound
     * packets at [level] lost.
     */
    fun onLossDetected(
        level: EncryptionLevel,
        lostPacketNumbers: List<Long>,
    )

    /**
     * RFC 9002 §6.2 PTO timer expired; the writer will emit a PING on
     * the next drain to elicit an ACK from the peer.
     *
     * @param consecutivePtoCount the new PTO count (post-increment).
     * @param ptoMillis the PTO duration that just expired.
     */
    fun onPtoFired(
        consecutivePtoCount: Int,
        ptoMillis: Long,
    )

    /**
     * Congestion-controller state transition (slow-start ↔
     * recovery ↔ congestion-avoidance). qvis renders this as
     * background bands on the RTT timeline.
     */
    fun onCongestionStateUpdated(newState: String)

    /**
     * QUIC transport parameters were set by [initiator] (`"local"` at
     * connection-open, `"remote"` once the peer's params arrive in
     * EncryptedExtensions). [params] is a flat label→value map of
     * the parameters that the implementation chose to surface; the
     * exact key set is not part of the qlog 0.3 contract.
     */
    fun onTransportParametersSet(
        initiator: String,
        params: Map<String, String>,
    )

    /**
     * The TLS handshake completed and an ALPN was selected (or `null`
     * was chosen — [alpn] is the human-readable name, e.g. `"h3"`).
     */
    fun onAlpnNegotiated(alpn: String)

    /**
     * RFC 9000 §6 Version Information. Single-fire — emitted once
     * after Version Negotiation resolves.
     */
    fun onVersionInformation(
        chosenVersion: String,
        otherVersionsOffered: List<String>,
    )

    /**
     * No-op observer. Default for production callers — every method
     * is an empty body that the JIT inlines. No allocation, no I/O.
     */
    object NoOp : QlogObserver {
        override fun onConnectionStarted(
            serverName: String,
            dcid: ByteArray,
            scid: ByteArray,
        ) = Unit

        override fun onConnectionClosed(
            initiator: String,
            errorCode: Long,
            reason: String,
        ) = Unit

        override fun onPacketSent(
            level: EncryptionLevel,
            packetNumber: Long,
            sizeBytes: Int,
            frames: List<String>,
        ) = Unit

        override fun onPacketReceived(
            level: EncryptionLevel,
            packetNumber: Long,
            sizeBytes: Int,
            frames: List<String>,
        ) = Unit

        override fun onPacketDropped(
            reason: String,
            sizeBytes: Int,
        ) = Unit

        override fun onKeyUpdated(
            keyType: String,
            level: EncryptionLevel,
        ) = Unit

        override fun onLossDetected(
            level: EncryptionLevel,
            lostPacketNumbers: List<Long>,
        ) = Unit

        override fun onPtoFired(
            consecutivePtoCount: Int,
            ptoMillis: Long,
        ) = Unit

        override fun onCongestionStateUpdated(newState: String) = Unit

        override fun onTransportParametersSet(
            initiator: String,
            params: Map<String, String>,
        ) = Unit

        override fun onAlpnNegotiated(alpn: String) = Unit

        override fun onVersionInformation(
            chosenVersion: String,
            otherVersionsOffered: List<String>,
        ) = Unit
    }
}

/**
 * Map a [com.vitorpamplona.quic.frame.Frame] subclass simple-name to
 * the qlog frame_type label. qlog 0.3 specifies snake_case with
 * `_frame` suffix stripped — e.g. `MaxStreamsFrame` → `max_streams`.
 *
 * Lives next to [QlogObserver] so writer + parser hot paths share the
 * same conversion (used to fill [QlogObserver.onPacketSent.frames] and
 * [QlogObserver.onPacketReceived.frames] without round-tripping through
 * Jackson).
 */
fun qlogFrameName(simpleClassName: String): String {
    // Strip trailing "Frame" then convert CamelCase to snake_case.
    val noSuffix =
        if (simpleClassName.endsWith("Frame")) {
            simpleClassName.dropLast("Frame".length)
        } else {
            simpleClassName
        }
    if (noSuffix.isEmpty()) return simpleClassName.lowercase()
    val sb = StringBuilder(noSuffix.length + 4)
    for (i in noSuffix.indices) {
        val c = noSuffix[i]
        if (i > 0 && c.isUpperCase()) sb.append('_')
        sb.append(c.lowercaseChar())
    }
    return sb.toString()
}
