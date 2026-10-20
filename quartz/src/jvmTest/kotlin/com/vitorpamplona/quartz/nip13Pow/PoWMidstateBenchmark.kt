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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.crypto.sha2.Sha256Midstate
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.MiningBuffer
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.indexOf
import com.vitorpamplona.quartz.nip13Pow.miner.prefersMidstate
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Single-thread hash rate of [PoWMiner] hashing the whole serialized event per
 * candidate (MessageDigest) against hashing only from the nonce's block on
 * ([Sha256Midstate], pure Kotlin).
 *
 * Each pass enumerates exactly the last [VARYING] nonce bytes at an unreachable
 * difficulty, so both modes hash the same fixed number of candidates.
 * Opt-in: `-DpowBenchRounds=5`.
 */
class PoWMidstateBenchmark {
    private val rounds = System.getProperty("powBenchRounds")?.toInt() ?: 0
    private val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private val replyTags =
        arrayOf(
            arrayOf("e", "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3", "", "root"),
            arrayOf("e", "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc", "", "reply"),
            arrayOf("p", "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"),
        )

    private val bigThreadTags =
        replyTags +
            listOf(
                "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24",
                "ec4d241c334311b3a304433ee3442be29d0e88e7ec19b85edf2bba29b93565e2",
                "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac",
                "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168",
                "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed",
                "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0",
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            ).map { arrayOf("p", it) }

    private val scenarios =
        listOf(
            "short note" to template(emptyArray(), "gm"),
            "tweet-size note" to template(emptyArray(), "Just mined this note with a few leading zero bits, because why not. #nostr #pow"),
            "reply, short content" to template(replyTags, "+1"),
            "reply, tweet-size content" to template(replyTags, "Just mined this note with a few leading zero bits, because why not. #nostr #pow"),
            "big thread reply, short content" to template(bigThreadTags, "agreed"),
            "big thread reply, 400B content" to template(bigThreadTags, "Amethyst agrees with Astral which I suspect are both wrong. ".repeat(7)),
            "long note, no tags" to template(emptyArray(), "A long form thought. ".repeat(100)),
        )

    private fun template(
        tags: Array<Array<String>>,
        content: String,
    ) = EventTemplate<TextNoteEvent>(1683596206, TextNoteEvent.KIND, tags, content)

    private class Prepared(
        val bytes: ByteArray,
        val nonceStart: Int,
        val nonceLength: Int,
    )

    private fun prepare(template: EventTemplate<TextNoteEvent>): Prepared {
        val nonce = "0000000000"
        val bytes = EventHasherSerializer.fastMakeJsonForId(pubKey, template.createdAt, template.kind, template.tags + PoWTag.assemble(nonce, 256), template.content)
        return Prepared(bytes, bytes.indexOf(nonce.encodeToByteArray()), nonce.length)
    }

    /** Hashes per second for one exhausted pass. */
    private fun pass(
        p: Prepared,
        useMidstate: Boolean,
    ): Double {
        val midstate = if (useMidstate) Sha256Midstate(p.bytes, p.nonceStart) else null
        val buffer =
            if (midstate != null) {
                val start = p.nonceStart - midstate.prefixLength
                MiningBuffer(midstate.tail, start, start + p.nonceLength)
            } else {
                MiningBuffer(p.bytes.copyOf(), p.nonceStart, p.nonceStart + p.nonceLength)
            }
        val miner = PoWMiner(buffer, 256, searchFrom = buffer.nonceEnds - VARYING, midstate = midstate)
        val start = System.nanoTime()
        assertFalse(miner.run())
        return CANDIDATES / ((System.nanoTime() - start) / 1e9)
    }

    @Test
    fun hashRate() {
        if (rounds <= 0) return

        println("SHA-256 provider: ${MessageDigest.getInstance("SHA-256").provider}  java ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        println(String.format("%-34s %6s %8s %8s %12s %12s %8s %10s", "scenario", "bytes", "skipped", "hashed", "full MH/s", "midstate MH/s", "speedup", "AUTO picks"))
        for ((name, template) in scenarios) {
            val p = prepare(template)
            val m = Sha256Midstate(p.bytes, p.nonceStart)
            val fullBlocks = (p.bytes.size + 9 + 63) / 64

            // before the warm-up, as a real job would see it; the first scenario
            // runs it on a cold JIT.
            val trialStart = System.nanoTime()
            val auto = if (prefersMidstate(p.bytes, p.nonceStart, p.nonceLength, 24)) "midstate" else "full"
            val trialMs = (System.nanoTime() - trialStart) / 1e6

            // warm-up rounds let the JIT compile both paths before timing
            repeat(2) {
                pass(p, false)
                pass(p, true)
            }
            val full = (1..rounds).map { pass(p, false) }.sorted()[rounds / 2]
            val mid = (1..rounds).map { pass(p, true) }.sorted()[rounds / 2]
            println(
                String.format(
                    "%-34s %6d %8d %8d %12.2f %12.2f %7.2fx %10s",
                    name,
                    p.bytes.size,
                    m.prefixLength / 64,
                    m.tail.size / 64,
                    full / 1e6,
                    mid / 1e6,
                    mid / full,
                    auto,
                ) + String.format("   (blocks: %d full, trial %.1f ms)", fullBlocks, trialMs),
            )
        }
    }

    companion object {
        const val VARYING = 3

        // 78-symbol alphabet at the VARYING last positions
        val CANDIDATES = 78.0 * 78.0 * 78.0
    }
}
