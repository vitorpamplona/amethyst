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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

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
