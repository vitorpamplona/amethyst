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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DEFAULT_STREAM_IN_FLIGHT
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventWithJson
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishRawStream
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

/**
 * `amy publish --file PATH --relay URL` — stream a file of signed events (one JSON
 * event per line, gzipped when the name ends in `.gz`, stdin for `-`) to one relay
 * through quartz's [publishRawStream], up to `--in-flight` unanswered at once. Each line
 * is parsed (for its id) and verified, then sent as it was read, never re-serialized.
 * Built for backfills and ingest benchmarks: the summary counts every OK by its reason
 * and reports the OK round trip's percentiles. Events that fail to parse or verify are
 * counted and never sent.
 */
object PublishFileCommand {
    /** Lines parsed and verified per parallel step. */
    private const val PARSE_CHUNK = 2_048

    /** Distinct OK-false reasons kept in the summary; the rest fold into one bucket. */
    private const val REASON_LIMIT = 32

    suspend fun run(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val path = args.requireFlag("file")
        val relays = RawEventSupport.relayFlag(args)
        val inFlight = args.intFlag("in-flight", DEFAULT_STREAM_IN_FLIGHT)
        val skip = args.longFlag("skip", 0)
        val take = args.longFlag("take", Long.MAX_VALUE)
        val verify = !args.bool("no-verify")
        val progressSecs = args.longFlag("progress", 0)
        val idleMs = args.timeoutMs(30)
        args.rejectUnknown()
        if (relays.size != 1) return Output.error("bad_args", "publish --file streams to exactly one --relay")
        if (inFlight < 1) return Output.error("bad_args", "--in-flight must be at least 1")
        val relay = relays.first()
        val input =
            if (path == "-") {
                System.`in`
            } else {
                File(path).takeIf { it.isFile }?.inputStream() ?: return Output.error("bad_args", "no such file: $path")
            }

        val tally = Tally()
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val started = System.nanoTime()
            val result =
                coroutineScope {
                    val progress = if (progressSecs > 0) launch { reportProgress(tally, started, progressSecs * 1000) } else null
                    try {
                        ctx.client.publishRawStream(relay, events(input, path.endsWith(".gz"), skip, take, verify, tally), inFlight, idleMs) { _, ok ->
                            tally.record(ok)
                        }
                    } finally {
                        progress?.cancel()
                    }
                }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            // Rates run to the last answer, so an idle window waited out at the end does not dilute them.
            val answeringMs =
                tally.lastAnswerNs
                    .get()
                    .takeIf { it > 0 }
                    ?.let { (it - started) / 1_000_000 } ?: elapsedMs
            Output.emit(summary(relay, tally, result.sent, result.stalled, elapsedMs, answeringMs))
            // A stall left part of the file unsent; a run nothing landed from is a failed run,
            // including one whose every line was dropped before it could be sent.
            val dropped = tally.unparseable.get() + tally.badSignatures.get()
            val nothingLanded = tally.accepted.get() + tally.duplicates.get() == 0L && (result.sent > 0 || dropped > 0)
            return if (result.stalled || nothingLanded) 1 else 0
        }
    }

    /** Parsed (and, unless told not to, verified) events, [skip] lines in, at most [take] of them. */
    private fun events(
        input: InputStream,
        gzipped: Boolean,
        skip: Long,
        take: Long,
        verify: Boolean,
        tally: Tally,
    ): Flow<EventWithJson> =
        flow {
            val reader: BufferedReader = (if (gzipped) GZIPInputStream(input, 1 shl 16) else input).bufferedReader()
            reader.use {
                val lines = it.lineSequence().filter { line -> line.isNotBlank() }.drop(skip.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                var left = take
                for (chunk in lines.chunked(PARSE_CHUNK)) {
                    if (left <= 0) break
                    val slice = if (chunk.size > left) chunk.subList(0, left.toInt()) else chunk
                    left -= slice.size
                    for (event in parse(slice, verify, tally)) emit(event)
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun parse(
        lines: List<String>,
        verify: Boolean,
        tally: Tally,
    ): List<EventWithJson> =
        coroutineScope {
            val parts = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            lines
                .chunked((lines.size + parts - 1) / parts)
                .map { part ->
                    async(Dispatchers.Default) {
                        part.mapNotNull { line ->
                            val event = runCatching { Event.fromJson(line) }.getOrNull()
                            when {
                                event == null -> {
                                    tally.unparseable.incrementAndGet()
                                    null
                                }

                                verify && !event.verify() -> {
                                    tally.badSignatures.incrementAndGet()
                                    null
                                }

                                else -> {
                                    tally.bytes.addAndGet(line.length.toLong())
                                    // The line itself is what gets sent: no second serialization.
                                    EventWithJson(event, line)
                                }
                            }
                        }
                    }
                }.awaitAll()
                .flatten()
        }

    private suspend fun reportProgress(
        tally: Tally,
        started: Long,
        everyMs: Long,
    ) {
        var lastOks = 0L
        var lastAt = started
        while (true) {
            delay(everyMs)
            val now = System.nanoTime()
            val oks = tally.answered()
            val rate = (oks - lastOks) * 1_000_000_000.0 / (now - lastAt)
            System.err.println(
                "publish: ${(now - started) / 1_000_000_000}s $oks answered (" +
                    "${tally.accepted.get()} accepted, ${tally.duplicates.get()} duplicate, ${tally.refused.get()} refused, " +
                    "${tally.unanswered.get()} unanswered) ${"%.0f".format(rate)} ev/s",
            )
            lastOks = oks
            lastAt = now
        }
    }

    private fun summary(
        relay: NormalizedRelayUrl,
        tally: Tally,
        sent: Int,
        stalled: Boolean,
        elapsedMs: Long,
        answeringMs: Long,
    ): Map<String, Any?> {
        val ms = tally.latencies()

        fun pct(p: Double): Long = if (ms.isEmpty()) 0 else ms[((ms.size - 1) * p).toInt()]
        return linkedMapOf(
            "relay" to relay.url,
            "sent" to sent,
            "accepted" to tally.accepted.get(),
            "duplicate" to tally.duplicates.get(),
            "refused" to tally.refused.get(),
            "unanswered" to tally.unanswered.get(),
            "refused_reasons" to tally.reasons(),
            "unparseable" to tally.unparseable.get(),
            "bad_signatures" to tally.badSignatures.get(),
            "stalled" to stalled,
            "bytes" to tally.bytes.get(),
            "elapsed_ms" to elapsedMs,
            "answering_ms" to answeringMs,
            "events_per_sec" to if (answeringMs > 0) Math.round(tally.answered() * 1000.0 / answeringMs) else 0,
            "mb_per_sec" to if (answeringMs > 0) Math.round(tally.bytes.get() / 1000.0 / answeringMs * 100) / 100.0 else 0.0,
            "ok_ms" to linkedMapOf("p50" to pct(0.50), "p90" to pct(0.90), "p99" to pct(0.99), "max" to (ms.lastOrNull() ?: 0)),
        )
    }

    /** Counters shared by the OK callback, the parser and the progress line. */
    private class Tally {
        val accepted = AtomicLong()
        val duplicates = AtomicLong()
        val refused = AtomicLong()
        val unanswered = AtomicLong()
        val unparseable = AtomicLong()
        val badSignatures = AtomicLong()
        val bytes = AtomicLong()
        val lastAnswerNs = AtomicLong()
        private val reasons = HashMap<String, Int>()
        private val okMs = LongList()

        fun answered(): Long = accepted.get() + duplicates.get() + refused.get() + unanswered.get()

        /** NIP-01 answers a stored event with `OK true` and a `duplicate:` reason; counted on its own. */
        fun record(ok: PublishResult) {
            if (!ok.isTransportFailure) lastAnswerNs.set(System.nanoTime())
            when {
                ok.isTransportFailure -> unanswered.incrementAndGet()
                ok.message.startsWith("duplicate:") -> duplicates.incrementAndGet()
                ok.accepted -> accepted.incrementAndGet()
                else -> refused.incrementAndGet()
            }
            synchronized(this) {
                if (ok.elapsedMs >= 0) okMs.add(ok.elapsedMs)
                if (!ok.accepted && !ok.isTransportFailure) {
                    // The machine-readable prefix plus a little of the sentence: per-event detail would mint a key per event.
                    val key = ok.message.take(48)
                    if (reasons.size < REASON_LIMIT || key in reasons) reasons[key] = (reasons[key] ?: 0) + 1 else reasons[OTHER] = (reasons[OTHER] ?: 0) + 1
                }
            }
        }

        fun reasons(): Map<String, Int> = synchronized(this) { reasons.entries.sortedByDescending { it.value }.associate { it.key to it.value } }

        fun latencies(): LongArray = synchronized(this) { okMs.toSortedArray() }

        companion object {
            const val OTHER = "(other reasons)"
        }
    }

    /** A growable primitive list, so millions of round trips do not box. */
    private class LongList {
        private var values = LongArray(1 shl 16)
        private var size = 0

        fun add(v: Long) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = v
        }

        fun toSortedArray(): LongArray = values.copyOf(size).also { it.sort() }
    }
}
