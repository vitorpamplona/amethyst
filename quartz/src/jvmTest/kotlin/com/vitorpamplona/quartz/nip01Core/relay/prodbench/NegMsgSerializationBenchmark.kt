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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Measures the NEG-MSG wire-serialization fix and guards its correctness.
 * geode's server-path JFR attributed a large slice of per-round reconcile
 * time to turning the reconcile frame into wire JSON: `Hex.encode` → the
 * generic (Jackson) [com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper]
 * serializer, which wraps the ~1 MB hex string in a value node and scans
 * every char for JSON escapes a `[0-9a-f]` payload can never contain.
 *
 * [NegMsgMessage.toJson] now builds `["NEG-MSG","<sub>","<hex>"]` directly
 * (fast path for escape-free ASCII subIds; generic fallback otherwise).
 * This asserts the fast path is **byte-identical** to the generic path
 * across a battery of subIds — including exotic ones that must hit the
 * fallback — and times the two at realistic frame sizes.
 */
class NegMsgSerializationBenchmark {
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

    @Test
    fun fastPathIsByteIdenticalToGeneric() {
        val hex = hexPayload(1024)
        val subIds =
            listOf(
                "bench-sync",
                "sub_123",
                "a".repeat(64),
                "", // empty
                "with\"quote", // → fallback
                "with\\backslash", // → fallback
                "tab\there", // → fallback
                "new\nline", // → fallback
                "unicode-é中", // non-ASCII → fallback
                "ctrlchar", // → fallback
            )
        for (sub in subIds) {
            val msg = NegMsgMessage(sub, hex)
            assertEquals(
                OptimizedJsonMapper.toJson(msg),
                msg.toJson(),
                "toJson() must match the generic serializer for subId=<$sub>",
            )
        }
    }

    private fun bench(
        label: String,
        rawFrameBytes: Int,
    ) {
        val hex = hexPayload(rawFrameBytes)
        val msg = NegMsgMessage("bench-sync", hex)

        val warmup = 200
        val runs = 500
        var sink = 0
        repeat(warmup) { sink = sink xor msg.toJson().length xor OptimizedJsonMapper.toJson(msg).length }

        // Include the UTF-8 encode a websocket text frame pays either way.
        val t0 = System.nanoTime()
        repeat(runs) { sink = sink xor OptimizedJsonMapper.toJson(msg).encodeToByteArray().size }
        val genMs = (System.nanoTime() - t0) / 1e6 / runs

        val t1 = System.nanoTime()
        repeat(runs) { sink = sink xor msg.toJson().encodeToByteArray().size }
        val fastMs = (System.nanoTime() - t1) / 1e6 / runs

        println("  %-16s generic %6.3f ms   fast %6.3f ms   %.1f×  (sink=%d)".format(label, genMs, fastMs, genMs / fastMs, sink and 1))
    }

    @Test
    fun negMsgWireSerializationSpeedup() {
        println("─ NegMsgSerializationBenchmark: generic vs fast toJson()+utf8 ─")
        bench("64 KiB frame", 64 * 1024)
        bench("250 KiB frame", 250 * 1024)
        bench("500 KiB frame", 500 * 1024) // strfry's frame cap
    }
}
