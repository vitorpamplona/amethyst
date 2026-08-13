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
package com.vitorpamplona.quartz.nip13Pow.miner

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256Into
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class PoWMiner(
    val buffer: MiningBuffer,
    val desiredPoW: Int,
    val isActive: () -> Boolean = { true },
    // first nonce byte the search is allowed to change; bytes between
    // nonceStarts and this index stay fixed (a parallel worker's prefix).
    val searchFrom: Int = buffer.nonceStarts,
    // ends the current pass without cancelling it, so the caller can restamp
    // created_at and search a brand-new space. Polled alongside [isActive].
    val isPassOver: () -> Boolean = { false },
) {
    val emptyBytesForDesiredPoW = desiredPoW / 8

    // sha256Into writes every attempt's hash here instead of allocating a fresh
    // 32-byte array per hash, keeping the hot loop allocation-free.
    private val hashOut = ByteArray(32)

    /**
     * True when the last [run] stopped because [isPassOver] flipped rather than
     * because the nonce space ran out — the space is still unexplored, so the
     * caller should retry it under a new created_at instead of widening it.
     */
    var passedOver = false
        private set

    fun reachedDesiredPoW(byteArray: ByteArray) = PoWRankEvaluator.atLeastPowRank(sha256Into(hashOut, byteArray, byteArray.size), desiredPoW, emptyBytesForDesiredPoW)

    fun run(): Boolean {
        passedOver = false
        return runDigit(searchFrom)
    }

    private fun runDigit(index: Int): Boolean {
        // checks once every VALID_BYTES.size^2 hashes: cheap enough to not slow
        // mining down, frequent enough for cancellation to feel immediate.
        if (index + 2 <= buffer.nonceEnds) {
            if (!isActive()) throw CancellationException("PoW mining was cancelled")

            if (isPassOver()) {
                passedOver = true
                return false
            }
        }

        for (testByte in VALID_BYTES) {
            // replaces the background base by the nonce integers
            buffer.bytes[index] = testByte

            if (index + 1 < buffer.nonceEnds) {
                if (runDigit(index + 1)) return true
                // unwind the whole recursion rather than stepping to the next
                // byte: the pass is over, not just this branch.
                if (passedOver) return false
            } else {
                if (reachedDesiredPoW(buffer.bytes)) return true
            }
        }
        return false
    }

    companion object {
        private const val STARTING_NONCE_SIZE = 5

        /**
         * How long one pass searches before it stops to restamp created_at.
         * created_at has one-second resolution, so a shorter pass would rebuild
         * the payload for the same timestamp and a longer one would let the
         * post go stale. Only applies when a clock is supplied.
         */
        private val PASS_BUDGET = 1.seconds

        // make sure these chars are not escaped by the JSON stringifier
        private val VALID_CHARS: List<Char> =
            ('0'..'9') + ('a'..'z') + ('A'..'Z') + "-()[]{}$@!*=;:?,".toCharArray().toList()

        private val VALID_BYTES = VALID_CHARS.map { it.code.toByte() }

        private fun randomBase(size: Int): String = CharArray(size) { VALID_CHARS.random() }.concatToString()

        /**
         * The miner creates a stringified json template and changes the nonce directly in the UTF-8 ByteArray representation
         * to avoid having to recompute the json objects and stringify it.
         *
         * The search enumerates the nonce space deterministically ([VALID_BYTES]
         * in order at every position), so for a given template and pubkey every
         * call hashes the same sequence of candidates.
         *
         * [isActive] is polled while mining; returning false aborts the search with a
         * [CancellationException] so callers can cancel long-running jobs cooperatively.
         *
         * [refreshCreatedAt] opts into NIP-13's *"it is recommended to update the
         * `created_at` as well during this process"*: the search restamps the
         * template from that clock roughly once a second, so a post that mines for
         * minutes does not publish minutes in the past. The returned template
         * carries the timestamp the nonce actually commits to. Leave it null
         * wherever created_at is meaningful — scheduled posts, NIP-59 wraps with
         * deliberately randomized timestamps — and the search stays frozen exactly
         * as before.
         */
        fun <T : Event> run(
            template: EventTemplate<T>,
            pubKey: HexKey,
            desiredPoW: Int,
            isActive: () -> Boolean = { true },
            refreshCreatedAt: (() -> Long)? = null,
        ): EventTemplate<T> = search(template, pubKey, desiredPoW, isActive, "", refreshCreatedAt)

        /**
         * [noncePrefix] is kept verbatim at the front of the nonce while only the
         * bytes after it are enumerated — parallel workers get distinct prefixes
         * so their search spaces never overlap.
         */
        private fun <T : Event> search(
            template: EventTemplate<T>,
            pubKey: HexKey,
            desiredPoW: Int,
            isActive: () -> Boolean,
            noncePrefix: String,
            refreshCreatedAt: (() -> Long)?,
        ): EventTemplate<T> {
            // sha256 ids have 256 bits; anything outside would index past the
            // hash (or never terminate) deep inside the hot loop.
            require(desiredPoW in 1..256) { "desiredPoW must be in 1..256, was $desiredPoW" }

            var nextSize = STARTING_NONCE_SIZE
            var createdAt = template.createdAt

            do {
                // never backwards: a wall clock that steps back (or a template
                // deliberately stamped ahead) must not drag the post into the past.
                if (refreshCreatedAt != null) createdAt = maxOf(createdAt, refreshCreatedAt())

                val initialNonce = noncePrefix + randomBase(nextSize)

                val bytes =
                    EventHasherSerializer
                        .fastMakeJsonForId(
                            pubKey = pubKey,
                            createdAt = createdAt,
                            kind = template.kind,
                            tags = template.tags + PoWTag.assemble(initialNonce, desiredPoW),
                            content = template.content,
                        )

                val startIndex = bytes.indexOf(initialNonce.encodeToByteArray())

                val buffer = MiningBuffer(bytes, startIndex, startIndex + initialNonce.length)

                val passStart = TimeSource.Monotonic.markNow()
                val isPassOver: () -> Boolean =
                    if (refreshCreatedAt != null) {
                        { passStart.elapsedNow() >= PASS_BUDGET }
                    } else {
                        { false }
                    }

                val miner = PoWMiner(buffer, desiredPoW, isActive, startIndex + noncePrefix.length, isPassOver)

                if (miner.run()) {
                    return EventTemplate(
                        createdAt,
                        template.kind,
                        template.tags + PoWTag.assemble(buffer.nonce(), desiredPoW),
                        template.content,
                    )
                } else if (!miner.passedOver) {
                    // only an exhausted space needs a wider nonce; a pass that
                    // stopped on the clock still has all of its own left to try
                    // under the next timestamp.
                    nextSize += STARTING_NONCE_SIZE
                }
                // with a clock the search never runs out of space — every restamp
                // opens a fresh one — so only isActive ends it.
            } while (refreshCreatedAt != null || nextSize < 50)

            throw RuntimeException("Could not find PoW")
        }

        /**
         * Distinct fixed nonce prefix for each racing worker, encoding the worker
         * index in base-[VALID_CHARS].size at the smallest width that fits
         * [workers] — one char up to 73 workers.
         */
        private fun workerPrefix(
            worker: Int,
            workers: Int,
        ): String {
            var width = 1
            var capacity = VALID_CHARS.size
            while (capacity < workers) {
                width++
                capacity *= VALID_CHARS.size
            }

            var remaining = worker
            val prefix = CharArray(width)
            for (i in width - 1 downTo 0) {
                prefix[i] = VALID_CHARS[remaining % VALID_CHARS.size]
                remaining /= VALID_CHARS.size
            }
            return prefix.concatToString()
        }

        /**
         * Multi-core variant of [run]: races [workers] searches over disjoint
         * slices of the nonce space (each worker's nonce carries a distinct fixed
         * prefix) and returns the first template to reach [desiredPoW]. Workers
         * share nothing but the finish flag, so the hash rate scales roughly
         * linearly with cores.
         *
         * Throws the same [CancellationException] as [run] when [isActive] flips
         * false before a nonce is found.
         *
         * [refreshCreatedAt] behaves as in [run]. Workers restamp independently,
         * so the winner's template carries its own timestamp — which is the one
         * its nonce commits to.
         */
        suspend fun <T : Event> mine(
            template: EventTemplate<T>,
            pubKey: HexKey,
            desiredPoW: Int,
            workers: Int = 1,
            isActive: () -> Boolean = { true },
            refreshCreatedAt: (() -> Long)? = null,
        ): EventTemplate<T> {
            require(workers >= 1) { "workers must be >= 1, was $workers" }
            if (workers == 1) return run(template, pubKey, desiredPoW, isActive, refreshCreatedAt)

            return coroutineScope {
                val winner = CompletableDeferred<EventTemplate<T>>()
                val race =
                    launch {
                        repeat(workers) { worker ->
                            launch(Dispatchers.Default) {
                                winner.complete(
                                    search(template, pubKey, desiredPoW, {
                                        isActive() && !winner.isCompleted
                                    }, workerPrefix(worker, workers), refreshCreatedAt),
                                )
                            }
                        }
                    }
                // every worker aborting without a win means the caller's isActive
                // flipped: rethrow the CancellationException the workers swallowed
                // (a losing worker's complete() is a no-op, so this can't clobber
                // a real result).
                race.invokeOnCompletion {
                    winner.completeExceptionally(CancellationException("PoW mining was cancelled"))
                }
                try {
                    winner.await()
                } finally {
                    race.cancel()
                }
            }
        }
    }
}
