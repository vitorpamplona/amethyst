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
package com.vitorpamplona.quic.interop.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quic.connection.EncryptionLevel
import com.vitorpamplona.quic.observability.QlogObserver
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * JSON-NDJSON qlog writer (qlog 0.3 / JSON-SEQ format) used by the
 * `:quic` interop runner. One JSON object per line; first line is the
 * qlog header, subsequent lines are events.
 *
 * Tools like qvis (https://qvis.quictools.info/) consume the resulting
 * `.sqlog` file to render sequence diagrams + RTT graphs + recovery
 * timelines.
 *
 * **Goal: every interop-runner test failure produces a qlog file the
 * caller can drop into qvis to see exactly what we did differently
 * from the spec.**
 *
 * Threading: [java.io.BufferedWriter] is not safe for concurrent
 * writers; we hold a [ReentrantLock] around each line emit so the
 * read + send loops can fire events concurrently without interleaving
 * partial JSON.
 */
class QlogWriter(
    file: File,
    private val odcidHex: String,
    private val mapper: ObjectMapper = DEFAULT_MAPPER,
    /** Wall-clock provider; tests inject a deterministic source. */
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : QlogObserver,
    Closeable {
    // append=false → truncate any prior trace at this path so a reused
    // QLOGDIR doesn't accumulate stale events from a previous run.
    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, false))

    /** Latched true once close() runs OR once a write throws IOException.
     *  Subsequent emits short-circuit instead of throwing into the
     *  send loop and tearing down an otherwise-healthy connection. */
    @Volatile
    private var closed: Boolean = false
    private val lock = ReentrantLock()
    private val startMillis: Long = nowMillis()

    /** Last wall-clock millis we flushed at. Periodic flushing avoids the
     *  "qlog truncates at exactly the BufferedWriter buffer size when the
     *  JVM is hard-killed at runner timeout" trap that hides the test's
     *  late behavior. The interval is generous enough to keep the macOS
     *  Docker filesystem virtualization overhead off the hot send path
     *  (per-event flush there was multi-ms and broke handshakes — see
     *  commit 99a1a91de). 250 ms keeps us within ~one cwnd of the
     *  failure under loss while still being far cheaper than per-event. */
    @Volatile
    private var lastFlushMillis: Long = startMillis

    init {
        // qlog 0.3 JSON-SEQ header. qvis tolerates both `qlog_format`
        // values "JSON-SEQ" and "NDJSON"; we use JSON-SEQ to match the
        // most-common production qlog files (Chromium, mvfst).
        val header =
            mapOf(
                "qlog_version" to "0.3",
                "qlog_format" to "JSON-SEQ",
                "title" to "amethyst :quic client trace",
                "trace" to
                    mapOf(
                        "vantage_point" to mapOf("type" to "client", "name" to "amethyst-quic"),
                        "common_fields" to
                            mapOf(
                                "ODCID" to odcidHex,
                                "reference_time" to startMillis,
                                "time_format" to "relative",
                            ),
                    ),
            )
        writeLineLocked(mapper.writeValueAsString(header))
    }

    override fun onConnectionStarted(
        serverName: String,
        dcid: ByteArray,
        scid: ByteArray,
    ) {
        emit(
            "transport:connection_started",
            mapOf(
                "ip_version" to "v4_or_v6",
                "server_name" to serverName,
                "dst_cid" to hex(dcid),
                "src_cid" to hex(scid),
            ),
        )
    }

    override fun onConnectionClosed(
        initiator: String,
        errorCode: Long,
        reason: String,
    ) {
        emit(
            "transport:connection_closed",
            mapOf(
                "owner" to initiator,
                "application_code" to errorCode,
                "reason" to reason,
            ),
        )
    }

    override fun onPacketSent(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    ) {
        emit(
            "transport:packet_sent",
            mapOf(
                "header" to
                    mapOf(
                        "packet_type" to packetTypeFor(level),
                        "packet_number" to packetNumber,
                    ),
                "raw" to mapOf("length" to sizeBytes),
                "frames" to frames.map { mapOf("frame_type" to it) },
            ),
        )
    }

    override fun onPacketReceived(
        level: EncryptionLevel,
        packetNumber: Long,
        sizeBytes: Int,
        frames: List<String>,
    ) {
        emit(
            "transport:packet_received",
            mapOf(
                "header" to
                    mapOf(
                        "packet_type" to packetTypeFor(level),
                        "packet_number" to packetNumber,
                    ),
                "raw" to mapOf("length" to sizeBytes),
                "frames" to frames.map { mapOf("frame_type" to it) },
            ),
        )
    }

    override fun onPacketDropped(
        reason: String,
        sizeBytes: Int,
    ) {
        emit(
            "transport:packet_dropped",
            mapOf(
                "trigger" to reason,
                "raw" to mapOf("length" to sizeBytes),
            ),
        )
    }

    override fun onKeyUpdated(
        keyType: String,
        level: EncryptionLevel,
    ) {
        emit(
            "security:key_updated",
            mapOf(
                "key_type" to "${keyType}_${packetTypeFor(level)}_secret",
                "trigger" to "tls",
            ),
        )
    }

    override fun onLossDetected(
        level: EncryptionLevel,
        lostPacketNumbers: List<Long>,
    ) {
        for (pn in lostPacketNumbers) {
            emit(
                "recovery:packet_lost",
                mapOf(
                    "header" to
                        mapOf(
                            "packet_type" to packetTypeFor(level),
                            "packet_number" to pn,
                        ),
                    "trigger" to "reordering_threshold_or_time_threshold",
                ),
            )
        }
    }

    override fun onPtoFired(
        consecutivePtoCount: Int,
        ptoMillis: Long,
    ) {
        emit(
            "recovery:loss_timer_updated",
            mapOf(
                "event_type" to "expired",
                "timer_type" to "pto",
                "pto_count" to consecutivePtoCount,
                "delta" to ptoMillis,
            ),
        )
    }

    override fun onCongestionStateUpdated(newState: String) {
        emit(
            "recovery:congestion_state_updated",
            mapOf("new" to newState),
        )
    }

    override fun onTransportParametersSet(
        initiator: String,
        params: Map<String, String>,
    ) {
        emit(
            "transport:parameters_set",
            mapOf(
                "owner" to initiator,
                "params" to params,
            ),
        )
    }

    override fun onAlpnNegotiated(alpn: String) {
        emit(
            "transport:alpn_information",
            mapOf("chosen_alpn" to alpn),
        )
    }

    override fun onVersionInformation(
        chosenVersion: String,
        otherVersionsOffered: List<String>,
    ) {
        emit(
            "transport:version_information",
            mapOf(
                "chosen_version" to chosenVersion,
                "client_versions" to otherVersionsOffered,
            ),
        )
    }

    override fun close() {
        lock.withLock {
            closed = true
            try {
                writer.flush()
            } finally {
                writer.close()
            }
        }
    }

    private fun emit(
        name: String,
        data: Map<String, Any?>,
    ) {
        val event =
            linkedMapOf<String, Any?>(
                "time" to (nowMillis() - startMillis),
                "name" to name,
                "data" to data,
            )
        // Serialize OUTSIDE the lock so concurrent emitters don't
        // serialize their JSON serially. The lock is only held while
        // appending the line to the file.
        val line = mapper.writeValueAsString(event)
        writeLineLocked(line)
    }

    private fun writeLineLocked(line: String) {
        lock.withLock {
            // The send loop may still emit packet_sent events for the
            // CONNECTION_CLOSE packet after the application calls close()
            // — observers MUST NOT break the connection. Silently swallow
            // post-close IOExceptions; the trace is what we have.
            if (closed) return
            try {
                writer.write(line)
                writer.write("\n")
                // Periodic flush so a runner-timeout SIGKILL doesn't leave
                // the last seconds of the trace stranded in the JVM buffer.
                // Pre-fix this method never flushed (relied on close()), and
                // a 60 s test ended at exactly 32768 bytes = 4 × 8 KB
                // BufferedWriter blocks — masking 50 s of late-connection
                // behavior and turning every interop debug session into
                // "did the connection wedge or did the qlog?". Per-event
                // flush was the prior shape and was multi-ms on macOS
                // Docker virtualized filesystems (commit 99a1a91de). 250 ms
                // is the compromise: cheap enough to not stall the send
                // path, fine-grained enough to capture per-PTO behavior.
                val now = nowMillis()
                if (now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) {
                    writer.flush()
                    lastFlushMillis = now
                }
            } catch (_: java.io.IOException) {
                // Stream closed under us. Latch closed so subsequent emits
                // skip the lock and return immediately.
                closed = true
            }
        }
    }

    companion object {
        private const val FLUSH_INTERVAL_MILLIS: Long = 250L

        private val DEFAULT_MAPPER: ObjectMapper = jacksonObjectMapper()

        private fun packetTypeFor(level: EncryptionLevel): String =
            when (level) {
                EncryptionLevel.INITIAL -> "initial"
                EncryptionLevel.HANDSHAKE -> "handshake"
                EncryptionLevel.APPLICATION -> "1RTT"
            }

        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        fun hex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append(HEX_CHARS[v ushr 4])
                sb.append(HEX_CHARS[v and 0x0F])
            }
            return sb.toString()
        }
    }
}
