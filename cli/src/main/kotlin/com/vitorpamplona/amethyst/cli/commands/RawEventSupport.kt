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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import java.io.File

/**
 * Shared helpers for the nak-style raw-event verbs (`event`, `publish`,
 * `fetch`, `subscribe`, `count`). Kept tiny — parsing/normalisation only,
 * no protocol logic.
 */
object RawEventSupport {
    /**
     * Read a blob from the first positional argument, or from stdin when the
     * argument is omitted or `-`. Used by verbs that take event/filter JSON.
     */
    fun readArgOrStdin(args: Args): String {
        val arg = args.positionalOrNull(0)
        return if (arg == null || arg == "-") {
            System.`in`
                .readBytes()
                .decodeToString()
                .trim()
        } else {
            arg.trim()
        }
    }

    /**
     * Where a batch of events comes from: `--file PATH`, the first positional
     * argument, or stdin. Exactly one wins, in that order.
     */
    fun eventSource(args: Args): EventSource {
        val file = args.flag("file")
        if (file != null) return EventSource.File(file)
        val arg = args.positionalOrNull(0)
        return if (arg == null || arg == "-") EventSource.Stdin else EventSource.Literal(arg.trim())
    }

    sealed interface EventSource {
        data class File(
            val path: String,
        ) : EventSource

        data class Literal(
            val json: String,
        ) : EventSource

        data object Stdin : EventSource
    }

    /**
     * Yield one raw JSON blob per event from [source], accepting all three
     * shapes a caller might reasonably hand us:
     *
     *   * a single event object (what `amy publish '<json>'` has always taken),
     *   * JSONL — one compact event per line (what `amy fetch` prints, so
     *     `amy fetch … | amy publish --relay …` just works),
     *   * a JSON array of event objects.
     *
     * A `--file` of JSONL is streamed line by line rather than slurped, so a
     * multi-gigabyte dump publishes without being held in memory. Arrays and
     * literals are parsed whole — they have to be.
     *
     * Blank lines are skipped. A line that is not valid JSON is left for the
     * caller to report per-event; nothing is silently dropped.
     */
    fun readEvents(source: EventSource): Sequence<String> =
        when (source) {
            is EventSource.Literal -> splitBlob(source.json)
            EventSource.Stdin -> splitBlob(System.`in`.readBytes().decodeToString())
            is EventSource.File -> {
                val f = File(source.path)
                if (!f.isFile) throw IllegalArgumentException("no such file: ${source.path}")
                if (firstMeaningfulChar(f) == '[') {
                    splitBlob(f.readText())
                } else {
                    // Streamed: the whole point of --file for a big dump.
                    sequence {
                        f.bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                val t = line.trim()
                                if (t.isNotEmpty()) yield(t)
                            }
                        }
                    }
                }
            }
        }

    /** First non-whitespace byte of [file], or null when it is empty. */
    private fun firstMeaningfulChar(file: File): Char? {
        file.bufferedReader().use { reader ->
            while (true) {
                val c = reader.read()
                if (c < 0) return null
                if (!c.toChar().isWhitespace()) return c.toChar()
            }
        }
    }

    /**
     * Split an in-memory blob into per-event JSON. A leading `[` means a JSON
     * array; otherwise the blob is JSONL, except that a blob which parses whole
     * as one object is treated as the single pretty-printed event it looks like.
     */
    private fun splitBlob(blob: String): Sequence<String> {
        val trimmed = blob.trim()
        if (trimmed.isEmpty()) return emptySequence()
        if (trimmed.startsWith("[")) {
            val node = Output.mapper.readTree(trimmed)
            return node.map { it.toString() }.asSequence()
        }
        val lines =
            trimmed
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        if (lines.size > 1 && parsesWhole(trimmed)) {
            // Pretty-printed single object: many lines, but one JSON value.
            return sequenceOf(trimmed)
        }
        return lines.asSequence()
    }

    /**
     * True when [text] is ONE complete JSON value and nothing else.
     *
     * The trailing-token check is the whole point: a plain `readTree` on
     * JSONL happily returns the first object and silently ignores every
     * later line, which would publish 1 event out of 33,000.
     */
    private fun parsesWhole(text: String): Boolean =
        runCatching {
            Output.mapper
                .reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(text)
                ?.isObject == true
        }.getOrDefault(false)

    /** Parse a `--relay a,b,c` flag into normalized relay URLs; an un-normalizable entry is a `bad_args` failure. */
    fun relayFlag(args: Args): Set<NormalizedRelayUrl> =
        args
            .flag("relay")
            ?.split(',')
            ?.map { raw ->
                RelayUrlNormalizer.normalizeOrNull(raw.trim())
                    ?: throw IllegalArgumentException("invalid relay url: ${raw.trim()}")
            }?.toSet()
            .orEmpty()

    /**
     * The exit decision after a publish: `null` when at least one relay (or
     * no relay at all — a deliberately local-only build) accepted the event,
     * or a non-zero exit code after reporting `rejected` when every targeted
     * relay refused it. Callers use `publishGuard(ack, event.id)?.let { return it }`
     * so a total rejection stops a `set -e` script instead of exiting 0.
     * The error carries each relay's own reason (the NIP-01 OK message, a
     * connect error, or a timeout note) — the answer to "why didn't it post".
     */
    fun publishGuard(
        ack: Map<NormalizedRelayUrl, PublishResult>,
        eventId: String,
    ): Int? {
        if (ack.isEmpty() || ack.any { it.value.accepted }) return null
        // `rejected` means a relay actually answered `OK false`. When every
        // failure is transport-level (silent, unreachable, dropped) the honest
        // code is `timeout` (exit 124) — a retry-on-124 script should retry a
        // flaky network, not give up on a "rejection" no relay ever voiced.
        val genuinelyRejected = ack.values.any { !it.isTransportFailure }
        return if (genuinelyRejected) {
            Output.error(
                "rejected",
                "no relay accepted event $eventId",
                extra = mapOf("event_id" to eventId, "rejected_by" to rejectedBy(ack)),
            )
        } else {
            Output.error(
                "timeout",
                "no relay answered for event $eventId (all unreachable or silent)",
                extra = mapOf("event_id" to eventId, "rejected_by" to rejectedBy(ack)),
            )
        }
    }

    /**
     * The canonical relay-ack projection every publishing command emits:
     * `published_to` is the flat list of accepting relay URLs; `rejected_by`
     * is a list of `{relay, reason}` objects so a partial failure explains
     * itself. Use `Output.emit(mapOf(…) + RawEventSupport.ackFields(ack))`.
     */
    fun ackFields(ack: Map<NormalizedRelayUrl, PublishResult>): Map<String, Any?> =
        mapOf(
            "published_to" to ack.filterValues { it.accepted }.keys.map { it.url },
            "rejected_by" to rejectedBy(ack),
        )

    private fun rejectedBy(ack: Map<NormalizedRelayUrl, PublishResult>): List<Map<String, String>> =
        ack
            .filterValues { !it.accepted }
            .map { (relay, result) -> mapOf("relay" to relay.url, "reason" to result.message) }

    /**
     * Resolve where to publish: the explicit `--relay` set when given,
     * otherwise the account's NIP-65 outbox. Empty only when neither is
     * available (caller turns that into a `no_relays` error).
     */
    suspend fun publishTargets(
        ctx: Context,
        args: Args,
    ): Set<NormalizedRelayUrl> = relayFlag(args).ifEmpty { ctx.outboxRelays() }

    /**
     * Where to read from for `fetch` / `subscribe` / `count`: the explicit
     * `--relay` set, else the account's outbox, else the bootstrap union.
     */
    suspend fun queryTargets(
        ctx: Context,
        args: Args,
    ): Set<NormalizedRelayUrl> = relayFlag(args).ifEmpty { ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() } }

    /**
     * Assemble a NIP-01 [Filter] from the common query flags shared by
     * `fetch` / `subscribe` / `count`:
     *
     *   --kind 1,7        comma-separated kind ints
     *   --author a,b      comma-separated npub / nprofile / 64-hex (local decode only)
     *   --id x,y          comma-separated note / nevent / naddr / 64-hex
     *   --tag e=<id>,p=<pk>,t=nostr   generic single-letter tag filters
     *   --since / --until unix seconds
     *   --limit N
     *   --search TEXT     NIP-50
     *
     * Author/id decoding is local (no NIP-05 round-trip) — pass hex or a
     * bech32 entity. An unparseable entry is a `bad_args` failure: silently
     * dropping it would run the query with a *weaker* filter than the user
     * asked for and return silently-wrong results.
     */
    fun buildFilter(args: Args): Filter {
        val kinds =
            args
                .flag("kind")
                ?.split(',')
                ?.map { raw ->
                    raw.trim().toIntOrNull()
                        ?: throw IllegalArgumentException("--kind expects a number, got '${raw.trim()}'")
                }?.takeIf { it.isNotEmpty() }
        val authors =
            args
                .flag("author")
                ?.split(',')
                ?.map { raw ->
                    decodePublicKeyAsHexOrNull(raw.trim())
                        ?: throw IllegalArgumentException(
                            "--author expects npub/nprofile/64-hex, got '${raw.trim()}' " +
                                "(NIP-05 names need a network round-trip — resolve first with `amy profile show`)",
                        )
                }?.takeIf { it.isNotEmpty() }
        val ids =
            args
                .flag("id")
                ?.split(',')
                ?.map { raw ->
                    decodeEventIdAsHexOrNull(raw.trim())
                        ?: throw IllegalArgumentException("--id expects note/nevent/naddr/64-hex, got '${raw.trim()}'")
                }?.takeIf { it.isNotEmpty() }
        val tags =
            args
                .flag("tag")
                ?.split(',')
                ?.mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null else pair.take(idx).trim() to pair.substring(idx + 1).trim()
                }?.groupBy({ it.first }, { it.second })
                ?.takeIf { it.isNotEmpty() }

        return Filter(
            ids = ids,
            authors = authors,
            kinds = kinds,
            tags = tags,
            since = args.flag("since")?.let { it.toLongOrNull() ?: throw IllegalArgumentException("--since expects unix seconds, got '$it'") },
            until = args.flag("until")?.let { it.toLongOrNull() ?: throw IllegalArgumentException("--until expects unix seconds, got '$it'") },
            limit = args.flag("limit")?.let { it.toIntOrNull() ?: throw IllegalArgumentException("--limit expects a number, got '$it'") },
            search = args.flag("search"),
        )
    }
}
