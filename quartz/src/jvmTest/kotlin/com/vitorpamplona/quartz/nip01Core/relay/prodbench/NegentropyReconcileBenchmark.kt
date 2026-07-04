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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyServerSession
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In-process reproduction of relayBench's NIP-77 initial-reconcile phase,
 * with **no** network or JSON framing in the loop — so what it times is
 * exactly the server-side reconciliation work that the head-to-head
 * benchmark measured geode losing to strfry (1M corpus: geode 6,066 ms /
 * 27 rounds vs strfry 1,270 ms / 14 rounds).
 *
 * The harness plays the unbounded initiator (`frameSizeLimit = 0`, like
 * `SyncBenchmark.reconcile`); the [NegentropyServerSession] under test is
 * the exact object geode's [com.vitorpamplona.quartz.nip01Core.relay.server.NegSessionRegistry]
 * builds, at the same 500 KB frame cap. Server and client `processMessage`
 * time is summed separately so the server's share is isolated.
 *
 * Not a CI assertion on speed (container noise) — it asserts convergence
 * correctness and prints the breakdown. Size via `-DnegBenchN=1000000`.
 */
class NegentropyReconcileBenchmark {
    companion object {
        // Small by default so the correctness assertions run as a fast CI
        // regression guard; scale to the relayBench shape with -DnegBenchN.
        val N = System.getProperty("negBenchN")?.toInt() ?: 20_000
        const val FRAME_SIZE_LIMIT = 500_000L

        /** 2024-01-01. */
        const val BASE_TIME = 1_704_067_200L

        /**
         * Events per second. `created_at` is monotonic with index so that
         * sorted order == index order == a real chronological feed. That
         * makes SyncBenchmark's index slices ([0,0.8N) vs [0.2N,N)) fall on
         * *contiguous* time ranges — the diff is the oldest 20% + newest 20%,
         * exactly like relayBench's slices, not a scatter that forces the
         * reconciliation tree to split everywhere.
         */
        const val EVENTS_PER_SECOND = 3
    }

    /** splitmix64 — well-distributed 64 pseudo-random bits from a counter. */
    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private val hexChars = "0123456789abcdef".toCharArray()

    /** 64-char lowercase hex id from 4 mixed longs — distinct, unsorted. */
    private fun idFor(index: Int): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                val pos = (w * 8 + b) * 2
                out[pos] = hexChars[byte ushr 4]
                out[pos + 1] = hexChars[byte and 0xF]
            }
        }
        return String(out)
    }

    private fun entry(index: Int): IdAndTime {
        // Monotonic time with same-second ties (broken by the random id),
        // so index order matches negentropy's sorted order.
        val createdAt = BASE_TIME + index / EVENTS_PER_SECOND
        return IdAndTime(createdAt, idFor(index))
    }

    @Test
    fun serverReconcileAtCorpusScale() {
        // 80% / 80% slices with 60% overlap — SyncBenchmark's split.
        // Server holds [0.2N, N); client (initiator) holds [0, 0.8N).
        val clientEntries = (0 until (N * 8 / 10)).map { entry(it) }
        val serverEntries = ((N * 2 / 10) until N).map { entry(it) }

        val clientIds = clientEntries.mapTo(HashSet()) { it.id }
        val serverIds = serverEntries.mapTo(HashSet()) { it.id }
        val expectedNeed = serverIds.count { it !in clientIds } // server has, client lacks
        val expectedHave = clientIds.count { it !in serverIds } // client has, server lacks

        // Build (seal) time is measured separately — it's the NEG-OPEN cost,
        // not per-round reconcile.
        val sealStart = System.nanoTime()
        val server = NegentropyServerSession("bench", serverEntries, FRAME_SIZE_LIMIT)
        val sealMs = (System.nanoTime() - sealStart) / 1e6

        val client = NegentropySession("bench", Filter(), clientEntries, frameSizeLimit = 0)

        var rounds = 0
        var wireBytes = 0L
        var serverNanos = 0L
        var clientNanos = 0L
        val haveIds = HashSet<String>()
        val needIds = HashSet<String>()

        val open = client.open()
        var serverMsg: String? = open.initialMessage

        val loopStart = System.nanoTime()
        while (serverMsg != null) {
            wireBytes += serverMsg.length.toLong()

            val s0 = System.nanoTime()
            val response = server.processMessage(serverMsg)
            serverNanos += System.nanoTime() - s0

            if (response == null) break // server produced nothing → done
            wireBytes += response.message.length.toLong()
            rounds++

            val c0 = System.nanoTime()
            val result = client.processMessage(response.message)
            clientNanos += System.nanoTime() - c0

            haveIds += result.haveIds
            needIds += result.needIds
            serverMsg = result.nextCmd?.message
        }
        val loopMs = (System.nanoTime() - loopStart) / 1e6

        println("─ NegentropyReconcileBenchmark @ ${N / 1000}k (server ${serverEntries.size}, client ${clientEntries.size}) ─")
        println("  rounds:            $rounds")
        println("  wire:              ${"%.1f".format(wireBytes / 1024.0 / 1024.0)} MiB (hex NEG payloads, both directions)")
        println("  avg frame fill:    ${"%.0f".format(wireBytes.toDouble() / rounds / 1024)} KiB/round")
        println("  seal (NEG-OPEN):   ${"%.1f".format(sealMs)} ms")
        println("  server reconcile:  ${"%.1f".format(serverNanos / 1e6)} ms  ← the number to beat")
        println("  client reconcile:  ${"%.1f".format(clientNanos / 1e6)} ms")
        println("  loop wall:         ${"%.1f".format(loopMs)} ms")
        println("  need=${needIds.size} (exp $expectedNeed)  have=${haveIds.size} (exp $expectedHave)")

        assertEquals(expectedNeed, needIds.size, "need set")
        assertEquals(expectedHave, haveIds.size, "have set")
    }
}
