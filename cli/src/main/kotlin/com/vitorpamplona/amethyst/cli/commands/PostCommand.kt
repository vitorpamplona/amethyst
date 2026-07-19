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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * `amy post <text> [--relay URL …] [--pow BITS [--pow-timeout SECS]]` —
 * publish a NIP-10 kind:1 short text note to the user's outbox relays,
 * optionally mining a NIP-13 proof of work into it first. Mining blocks the
 * invocation (the CLI process IS the job); `--pow-timeout` aborts with exit
 * 124 and publishes nothing.
 *
 * Threading is intentionally out of scope here — `amy post` only handles new
 * top-level notes. Replies/quotes need richer event-hint plumbing and will get
 * their own verb when needed.
 */
object PostCommand {
    private const val MAX_DIFFICULTY = 64

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        // Args first, then the positional — so flags may appear anywhere
        // (`amy notes post --relay X "hi"` posts "hi", not "--relay").
        val args = Args(rest)
        val text =
            args.positionalOrNull(0)
                ?: return Output.error("bad_args", "post <text> [--relay URL …] [--pow BITS [--pow-timeout SECS]]")
        if (text.isBlank()) return Output.error("bad_args", "post text must not be blank")

        // Strictly validated like every other `--relay` in amy — a malformed
        // entry is a bad_args failure, not a silent drop.
        val extraRelays = RawEventSupport.relayFlag(args)

        val powRaw = args.flag("pow")
        val powTarget = powRaw?.toIntOrNull()
        if (powRaw != null && (powTarget == null || powTarget < 1 || powTarget > MAX_DIFFICULTY)) {
            return Output.error("bad_args", "--pow must be between 1 and $MAX_DIFFICULTY leading zero bits")
        }
        val powTimeoutSec = args.flag("pow-timeout")?.toLongOrNull()
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val outbox = ctx.outboxRelays()
            val targets = (outbox + extraRelays).toSet()
            if (targets.isEmpty()) {
                return Output.error("no_relays", "no outbox relays configured; pass --relay or run `amy relay add`")
            }

            val template = TextNoteEvent.build(text)

            var powMillis: Long? = null
            val readyToSign =
                if (powTarget != null) {
                    val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    System.err.println("mining $powTarget bits… ($threads threads)")
                    val deadlineNanos = powTimeoutSec?.let { System.nanoTime() + it * 1_000_000_000L }
                    val startedAt = System.nanoTime()
                    val mined =
                        try {
                            withContext(Dispatchers.Default) {
                                PoWMiner.mine(template, ctx.signer.pubKey, powTarget, threads) {
                                    deadlineNanos == null || System.nanoTime() < deadlineNanos
                                }
                            }
                        } catch (e: CancellationException) {
                            Output.error("timeout", "pow: did not reach $powTarget bits within ${powTimeoutSec}s; nothing was published")
                            return 124
                        }
                    powMillis = (System.nanoTime() - startedAt) / 1_000_000
                    System.err.println("mined in ${powMillis}ms")
                    mined
                } else {
                    template
                }

            val signed = ctx.signer.sign(readyToSign)
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }

            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "created_at" to signed.createdAt,
                    "content" to signed.content,
                    "pow" to if (powTarget != null) signed.pow() else null,
                    "pow_target" to powTarget,
                    "pow_millis" to powMillis,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
