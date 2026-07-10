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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.commitedPoW
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.pow
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong

/**
 * `amy pow <check|mine|bench>` — NIP-13 proof-of-work primitives.
 *
 * `check` and `bench` are stateless (no account, no network). `mine` works on
 * an UNSIGNED template: because the NIP-01 id does not commit to the
 * signature, amy can mine on behalf of any pubkey (NIP-13's delegated PoW) —
 * pass `--pubkey`, or omit it to mine for the active account.
 */
object PowCommands {
    private const val MAX_DIFFICULTY = 64

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "pow",
            tail,
            "pow <check|mine|bench> …",
            mapOf(
                "check" to { rest -> check(rest) },
                "mine" to { rest -> mine(dataDir, rest) },
                "bench" to { rest -> bench() },
            ),
        )

    /**
     * `amy pow check <event-json | ->` — difficulty of a SIGNED event, with the
     * NIP-13 commitment rule applied: `effective_pow` is capped at the committed
     * target and `valid` covers id+signature (a forged id can claim any PoW).
     */
    private fun check(rest: Array<String>): Int {
        val json = readPayload(rest) ?: return Output.error("bad_args", "pow check <event-json | -> (- reads stdin)")
        val event =
            try {
                Event.fromJson(json)
            } catch (e: Exception) {
                return Output.error("bad_event", e.message)
            }

        val commitment = event.tags.commitedPoW()
        Output.emit(
            mapOf(
                "event_id" to event.id,
                "valid" to event.verify(),
                "actual_bits" to PoWRankEvaluator.calculatePowRankOf(event.id),
                "committed_target" to commitment,
                "has_commitment" to (commitment != null),
                "effective_pow" to event.pow(),
            ),
        )
        return 0
    }

    /**
     * `amy pow mine --target N [--pubkey HEX] [--timeout SECS] <template-json | ->`
     * — mines an unsigned template and prints it back with the nonce tag, ready
     * to be signed by whoever owns the pubkey.
     */
    private suspend fun mine(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "pow mine --target N [--pubkey HEX] [--timeout SECS] <template-json | ->"

        val target = args.flags["target"]?.toIntOrNull() ?: return Output.error("bad_args", usage)
        if (target < 1 || target > MAX_DIFFICULTY) {
            return Output.error("bad_args", "--target must be between 1 and $MAX_DIFFICULTY")
        }

        val json = readPayload(args.positional.toTypedArray()) ?: return Output.error("bad_args", usage)
        val template =
            try {
                EventTemplate.fromJson(json)
            } catch (e: Exception) {
                return Output.error("bad_template", e.message)
            }

        val pubKey =
            args.flags["pubkey"]
                ?: try {
                    Context.open(dataDir).use { it.signer.pubKey }
                } catch (e: Exception) {
                    return Output.error("bad_args", "no account available; pass --pubkey (${e.message})")
                }
        if (pubKey.length != 64 || pubKey.any { it !in "0123456789abcdefABCDEF" }) {
            return Output.error("bad_args", "--pubkey must be 64 hex characters")
        }

        val timeoutSec = args.flags["timeout"]?.toLongOrNull()
        val deadlineNanos = timeoutSec?.let { System.nanoTime() + it * 1_000_000_000L }

        System.err.println("mining $target bits for ${pubKey.take(8)}…")
        val startedAt = System.nanoTime()

        val mined =
            try {
                withContext(Dispatchers.Default) {
                    PoWMiner.run(template, pubKey, target) {
                        deadlineNanos == null || System.nanoTime() < deadlineNanos
                    }
                }
            } catch (e: CancellationException) {
                Output.error("pow_timeout", "did not reach $target bits within ${timeoutSec}s")
                return 124
            }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val id =
            sha256(
                EventHasherSerializer.fastMakeJsonForId(
                    pubKey = pubKey,
                    createdAt = mined.createdAt,
                    kind = mined.kind,
                    tags = mined.tags,
                    content = mined.content,
                ),
            ).toHexKey()

        Output.emit(
            mapOf(
                "id" to id,
                "pubkey" to pubKey,
                "pow" to PoWRankEvaluator.calculatePowRankOf(id),
                "pow_target" to target,
                "pow_millis" to elapsedMs,
                "template_json" to mined.toJson(),
            ),
        )
        return 0
    }

    /** `amy pow bench` — hash rate + expected mining time per common target. */
    private suspend fun bench(): Int {
        val rate = PoWEstimator.hashesPerSecond()
        Output.emit(
            mapOf(
                "hashes_per_second" to rate.roundToLong(),
                "expected_seconds" to
                    listOf(16, 20, 24, 28).associate { bits ->
                        bits.toString() to PoWEstimator.estimateSeconds(bits, rate)
                    },
            ),
        )
        return 0
    }

    private fun readPayload(rest: Array<String>): String? {
        val arg = rest.firstOrNull() ?: return null
        val payload = if (arg == "-") System.`in`.readBytes().decodeToString() else arg
        return payload.trim().ifEmpty { null }
    }
}
