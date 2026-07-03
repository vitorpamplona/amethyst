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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.ParallelEventVerifier
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves [ParallelEventVerifier] delivers the parallel-verification speedup
 * that justifies moving Schnorr checks off the receiver coroutine: the same
 * signed events verified sequentially (today's inline behavior) vs submitted
 * through the batched parallel stage.
 *
 * Offline verify throughput measured earlier: ~75µs/event on one thread,
 * 2.6–3× across 4 cores (see the plan doc). The assertion threshold is
 * deliberately generous (1.5× on ≥4 cores) so CI noise doesn't flake.
 */
class ParallelVerifyBenchmark {
    companion object {
        const val EVENTS = 4_000
        const val SIGNERS = 8
    }

    /**
     * A fresh set per measured pass: Event caches derived state (e.g. its
     * serialized id form) after the first verification, so re-verifying the
     * SAME instances in a second pass under-measures — the two passes must
     * not share objects. Multiple signers mirror real feeds.
     */
    private fun signedEvents(salt: String): List<Event> {
        val signers = (1..SIGNERS).map { NostrSignerSync(KeyPair()) }
        return (1..EVENTS).map { signers[it % SIGNERS].sign(TextNoteEvent.build("parallel verify benchmark $salt $it", createdAt = it.toLong())) }
    }

    @Test
    fun batchedParallelVerifyBeatsSequential() {
        val seqEvents = signedEvents("seq")
        val parEvents = signedEvents("par")
        val cores = Runtime.getRuntime().availableProcessors()

        // warmup both paths (JIT + secp tables) on a disjoint set
        signedEvents("warmup").take(400).forEach { it.verify() }

        val seqStart = System.nanoTime()
        var seqOk = 0
        seqEvents.forEach { if (it.verify()) seqOk++ }
        val seqNanos = System.nanoTime() - seqStart

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val parNanos =
            try {
                runBlocking {
                    val verifier =
                        ParallelEventVerifier<Unit>(
                            scope = scope,
                            onVerified = { _, _ -> },
                        )
                    val start = System.nanoTime()
                    parEvents.forEach { verifier.submit(it, Unit) }
                    verifier.close()
                    withTimeout(60_000) { verifier.join() }
                    val nanos = System.nanoTime() - start
                    assertTrue(verifier.verifiedCount == parEvents.size.toLong(), "all valid events must verify")
                    assertTrue(verifier.invalidCount == 0L)
                    nanos
                }
            } finally {
                scope.cancel()
            }

        val speedup = seqNanos.toDouble() / parNanos
        println("=== PARALLEL VERIFY BENCHMARK ($EVENTS signed events, $cores cores) ===")
        println("  sequential (inline today): %.1fms (%.1fµs/event)".format(seqNanos / 1e6, seqNanos / 1e3 / EVENTS))
        println("  ParallelEventVerifier:     %.1fms (%.1fµs/event)  -> %.2fx".format(parNanos / 1e6, parNanos / 1e3 / EVENTS, speedup))

        if (cores >= 4) {
            assertTrue(speedup > 1.5, "expected >1.5x speedup on $cores cores, got %.2fx".format(speedup))
        }
    }
}
