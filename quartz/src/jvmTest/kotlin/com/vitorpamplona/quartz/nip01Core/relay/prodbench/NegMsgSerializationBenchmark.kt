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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Isolates the per-round NEG-MSG serialization tax that geode's server-path
 * JFR attributed ~40% of reconcile time to: a reconcile produces a binary
 * frame, `Hex.encode`s it to a ~1 MB hex String, then the outgoing [Message]
 * is turned into wire JSON by [com.vitorpamplona.quartz.nip01Core.kotlinSerialization.MessageKSerializer],
 * which builds a `JsonElement` tree (wrapping the giant hex string in a
 * `JsonPrimitive`) and re-serializes it — scanning every hex char for JSON
 * escapes that a `[0-9a-f]` payload can never contain — before Ktor UTF-8
 * encodes it to the socket.
 *
 * The wire is trivially `["NEG-MSG","<sub>","<hex>"]`, so a direct
 * `StringBuilder` skips the tree. This measures the current `toJson()` path
 * against that direct build (both followed by the UTF-8 encode a websocket
 * text frame pays), at realistic NEG-MSG sizes, and asserts they produce
 * byte-identical wire output.
 */
class NegMsgSerializationBenchmark {
    /** Direct wire build — `["NEG-MSG","<sub>","<hex>"]`, hex needs no escaping. */
    private fun directWire(
        subId: String,
        hex: String,
    ): String =
        buildString(hex.length + subId.length + 16) {
            append("[\"NEG-MSG\",")
            append(jsonString(subId)) // client-chosen subId: escape defensively
            append(',')
            append('"')
            append(hex) // pure hex, no escaping possible
            append("\"]")
        }

    /** Minimal JSON string encoder for the (short, usually-safe) subId. */
    private fun jsonString(s: String): String =
        buildString(s.length + 2) {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

    private fun hexPayload(rawBytes: Int): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(rawBytes * 2)
        var x = 0x9E3779B9.toInt()
        repeat(rawBytes * 2) {
            x = x * 1_103_515_245 + 12_345
            sb.append(chars[(x ushr 16) and 0xF])
        }
        return sb.toString()
    }

    private fun bench(
        label: String,
        rawFrameBytes: Int,
    ) {
        val subId = "bench-sync"
        val hex = hexPayload(rawFrameBytes)
        val msg = NegMsgMessage(subId, hex)

        // Correctness: direct build must equal the serializer's wire output.
        assertEquals(msg.toJson(), directWire(subId, hex), "wire mismatch for $label")

        val warmup = 200
        val runs = 500
        var sink = 0

        repeat(warmup) { sink = sink xor msg.toJson().length xor directWire(subId, hex).length }

        val t0 = System.nanoTime()
        repeat(runs) { sink = sink xor msg.toJson().encodeToByteArray().size }
        val curMs = (System.nanoTime() - t0) / 1e6 / runs

        val t1 = System.nanoTime()
        repeat(runs) { sink = sink xor directWire(subId, hex).encodeToByteArray().size }
        val fastMs = (System.nanoTime() - t1) / 1e6 / runs

        println("  %-18s cur %6.3f ms   direct %6.3f ms   %.1f×  (sink=%d)".format(label, curMs, fastMs, curMs / fastMs, sink and 1))
    }

    @Test
    fun negMsgWireSerialization() {
        println("─ NegMsgSerializationBenchmark: toJson()+utf8 vs direct build+utf8 ─")
        bench("64 KiB frame", 64 * 1024)
        bench("250 KiB frame", 250 * 1024)
        bench("500 KiB frame", 500 * 1024) // strfry's frame cap
    }
}
