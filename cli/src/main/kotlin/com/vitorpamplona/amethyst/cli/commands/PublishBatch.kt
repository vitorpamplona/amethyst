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

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Drives `amy publish` over a stream of events: verify, hand each to
 * [Context.publish], and tally the verdicts. Assembly only — the protocol
 * lives in `Context.publish` (which also owns the NIP-42 write-auth retry)
 * and in quartz's publish accessories.
 */
class PublishBatch(
    private val targets: Set<NormalizedRelayUrl>,
    private val timeoutSecs: Long,
    private val stopOnError: Boolean,
    private val concurrency: Int,
) {
    /**
     * Publish every event in [blobs]. The sequence is consumed in windows so a
     * multi-gigabyte JSONL stream never materialises as one huge list of
     * coroutines; within a window [concurrency] events are in flight at once.
     */
    suspend fun run(
        ctx: Context,
        blobs: Sequence<String>,
    ): Outcome {
        val gate = Semaphore(concurrency)
        val tally = Tally()

        outer@ for (window in blobs.chunked(concurrency * 8)) {
            val verdicts =
                coroutineScope {
                    window.map { blob -> async { gate.withPermit { publishOne(ctx, blob) } } }.awaitAll()
                }
            for (verdict in verdicts) {
                tally.record(verdict)
                if (stopOnError && tally.failures.isNotEmpty()) {
                    tally.stopped = true
                    break@outer
                }
            }
            tally.reportProgress()
        }
        return tally.toOutcome()
    }

    private suspend fun publishOne(
        ctx: Context,
        blob: String,
    ): Verdict {
        val event =
            try {
                Event.fromJson(blob)
            } catch (e: Exception) {
                return Verdict.Invalid("(unparseable)", "not a valid event: ${e.message}")
            }
        if (!event.verify()) return Verdict.Invalid(event.id, INVALID_SIGNATURE)
        return Verdict.Sent(event, ctx.publish(event, targets, timeoutSecs))
    }

    private sealed interface Verdict {
        class Invalid(
            val eventId: String,
            val reason: String,
        ) : Verdict

        class Sent(
            val event: Event,
            val ack: Map<NormalizedRelayUrl, PublishResult>,
        ) : Verdict
    }

    /** Running counts; also remembers the lone event of a size-1 batch. */
    private class Tally {
        val failures = mutableListOf<Failure>()
        val perRelay = linkedMapOf<String, IntArray>()
        var total = 0
        var published = 0
        var duplicates = 0
        var invalid = 0
        var stopped = false
        private var lastReported = 0
        private var first: Pair<Event, Map<NormalizedRelayUrl, PublishResult>>? = null

        fun record(verdict: Verdict) {
            total++
            when (verdict) {
                is Verdict.Invalid -> {
                    invalid++
                    failures.add(Failure(verdict.eventId, verdict.reason, transportOnly = false))
                }

                is Verdict.Sent -> {
                    if (total == 1) first = verdict.event to verdict.ack
                    var accepted = false
                    var alreadyThere = false
                    verdict.ack.forEach { (relay, result) ->
                        val counts = perRelay.getOrPut(relay.url) { IntArray(3) }
                        when {
                            result.accepted -> {
                                counts[0]++
                                accepted = true
                            }

                            isDuplicate(result.message) -> {
                                counts[2]++
                                alreadyThere = true
                            }

                            else -> counts[1]++
                        }
                    }
                    if (accepted) {
                        published++
                    } else if (alreadyThere) {
                        // The relay already has it. That is the goal of a mirror
                        // run, not a failure — counting it as one would make a
                        // re-run of the same batch "fail" 100%.
                        duplicates++
                    } else {
                        failures.add(
                            Failure(
                                verdict.event.id,
                                verdict.ack.values
                                    .firstOrNull()
                                    ?.message ?: "no relay answered",
                                transportOnly = verdict.ack.values.all { it.isTransportFailure },
                            ),
                        )
                    }
                }
            }
        }

        /** Progress belongs on stderr — stdout carries exactly one result. */
        fun reportProgress() {
            if (total - lastReported < PROGRESS_EVERY) return
            lastReported = total
            System.err.println(
                "publish: $total sent — $published new, $duplicates already there, ${failures.size} failed",
            )
        }

        fun toOutcome() =
            Outcome(
                total = total,
                published = published,
                duplicates = duplicates,
                invalid = invalid,
                failures = failures.toList(),
                perRelay = perRelay.mapValues { RelayTally(it.value[0], it.value[1], it.value[2]) },
                single = first.takeIf { total == 1 },
                stopped = stopped,
            )
    }

    class Failure(
        val eventId: String,
        val reason: String,
        val transportOnly: Boolean,
    )

    /** One relay's share of a batch. */
    class RelayTally(
        val accepted: Int,
        val rejected: Int,
        val duplicate: Int,
    )

    class Outcome(
        val total: Int,
        val published: Int,
        /** Events the relay already had — `OK false` with NIP-01's `duplicate:` prefix. */
        val duplicates: Int,
        val invalid: Int,
        val failures: List<Failure>,
        val perRelay: Map<String, RelayTally>,
        /** Set only for a one-event batch, so the caller can keep the legacy shape. */
        val single: Pair<Event, Map<NormalizedRelayUrl, PublishResult>>?,
        val stopped: Boolean,
    ) {
        /**
         * The batch result shape — deliberately not the single-event one: a
         * caller publishing thousands of events needs counts and a per-relay
         * tally, not thousands of copies of `published_to`.
         */
        fun asResult(): Map<String, Any?> =
            mapOf(
                "total" to total,
                "published" to published,
                "duplicates" to duplicates,
                "failed" to failures.size - invalid,
                "invalid" to invalid,
                "stopped_early" to stopped,
                "published_to" to perRelay.filterValues { it.accepted > 0 || it.duplicate > 0 }.keys.toList(),
                "per_relay" to
                    perRelay.map { (relay, t) ->
                        mapOf(
                            "relay" to relay,
                            "accepted" to t.accepted,
                            "duplicates" to t.duplicate,
                            "rejected" to t.rejected,
                        )
                    },
                "failures" to
                    failures.take(MAX_REPORTED_FAILURES).map {
                        mapOf("event_id" to it.eventId, "reason" to it.reason)
                    },
                "failures_truncated" to (failures.size > MAX_REPORTED_FAILURES),
            )

        /**
         * Same honesty rule as the single-event [RawEventSupport.publishGuard]:
         * when nothing was ever actually refused, the failure was the network,
         * and 124 lets a retry-on-timeout script do the right thing.
         */
        fun exitCode(): Int {
            if (failures.isEmpty()) return 0
            val everRefused = invalid > 0 || failures.any { !it.transportOnly }
            return if (everRefused) 1 else 124
        }
    }

    companion object {
        /**
         * NIP-01 machine-readable prefix for "I already have this event". The
         * relay answers `OK false`, but for a mirror that is the desired end
         * state, so it is tallied separately from a real rejection.
         */
        fun isDuplicate(message: String): Boolean = message.trimStart().startsWith("duplicate:", ignoreCase = true)

        const val INVALID_SIGNATURE = "event id/signature does not verify"

        /** How many failures to name in the result before we just report the count. */
        const val MAX_REPORTED_FAILURES = 20
        private const val PROGRESS_EVERY = 500
    }
}
