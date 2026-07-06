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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * `amy fetch [--kind …] [--author …] [--id …] [--tag …] [--since/--until TS]
 *            [--limit N] [--search TEXT] [--relay URL[,URL…]] [--timeout SECS]
 *            [--paginate]`
 *
 *   amy fetch <nevent1…|naddr1…|nprofile1…|npub1…|note1…|name@domain>
 *
 * One-shot query: open a subscription, collect everything until every relay
 * sends EOSE (or the timeout fires), then print and exit. This is the bounded
 * half of nak's `req`; the live-streaming half is `amy subscribe`.
 *
 * Two modes:
 *  - **filter mode** (flags) — assemble a filter and query `--relay`/outbox.
 *  - **code mode** (a nip19/nip05 positional) — Amethyst's outbox-model
 *    resolution: query the relay hints embedded in the code PLUS the author's
 *    NIP-65 write relays, exactly how the app downloads an event/profile from
 *    a shared link. This is nak's `fetch` (nip19-hint resolution).
 *
 * Results are deduplicated by id, sorted newest-first, and emitted as full event
 * JSON under an `events` array.
 *
 * By default (filter mode) a single `REQ` is drained to EOSE — so a relay that
 * caps its response (strfry's per-`REQ` `limit`, ~500) truncates the result — and
 * the output is trimmed to the newest `--limit` (default 100). `--paginate` (alias
 * `--all`) instead walks each relay page-by-page on `until` cursors via the
 * multi-relay [Context.drainAllPages] path: with `--limit N` it pages up to N per
 * relay, and WITHOUT `--limit` it drains the whole filter unbounded (mind broad
 * filters — that can be a lot). Code mode is always single-shot.
 */
object FetchCommand {
    /** Output cap for a plain (non-`--paginate`) fetch when `--limit` is omitted. */
    private const val DEFAULT_LIMIT = 100

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        // `--limit` is optional. When absent, `--paginate` drains the whole filter
        // (unbounded) while a plain fetch still trims to DEFAULT_LIMIT.
        val explicitLimit = args.flag("limit")?.toIntOrNull()
        if (explicitLimit != null && explicitLimit <= 0) return Output.error("bad_args", "--limit must be > 0")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 8L) * 1000

        // Code mode: a nip19/nip05 positional resolves its own relays via the
        // outbox model rather than using a hand-built filter.
        args.positionalOrNull(0)?.takeIf { looksLikeCode(it) }?.let {
            return fetchByCode(dataDir, it, explicitLimit ?: DEFAULT_LIMIT, timeoutMs)
        }

        // buildFilter already carries `--limit` (or null) as the filter's limit, so
        // the paginate path uses it verbatim: bounded per relay with --limit, or a
        // full drain of the filter without one.
        val filter = RawEventSupport.buildFilter(args)
        val paginate = args.bool("paginate") || args.bool("all")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = RawEventSupport.queryTargets(ctx, args)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")

            val received =
                if (paginate) {
                    ctx.drainAllPages(relays.associateWith { listOf(filter) }, timeoutMs)
                } else {
                    ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
                }
            val ordered =
                received
                    .asSequence()
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
            // An explicit --limit always caps the output. Without it, a single-REQ
            // fetch trims to DEFAULT_LIMIT; a --paginate drain returns everything.
            val capped =
                when {
                    explicitLimit != null -> ordered.take(explicitLimit)
                    paginate -> ordered
                    else -> ordered.take(DEFAULT_LIMIT)
                }
            val events =
                capped
                    .map { Output.mapper.readTree(it.toJson()) }
                    .toList()

            Output.emit(
                mapOf(
                    "queried_relays" to relays.map { it.url },
                    "count" to events.size,
                    "events" to events,
                ),
            )
            return 0
        }
    }

    private fun looksLikeCode(s: String): Boolean {
        val t = s.removePrefix("nostr:")
        return t.startsWith("npub1") || t.startsWith("nprofile1") || t.startsWith("nevent1") ||
            t.startsWith("naddr1") || t.startsWith("note1") || ('@' in t && '.' in t)
    }

    /**
     * Outbox-model fetch from a nip19/nip05 code: decode it into a filter +
     * relay hints + author, then query the hint relays UNION the author's
     * NIP-65 write relays — the same resolution the Android app uses to open a
     * shared `nevent`/`naddr`/profile link.
     */
    private suspend fun fetchByCode(
        dataDir: DataDir,
        codeArg: String,
        limit: Int,
        timeoutMs: Long,
    ): Int {
        val code = codeArg.removePrefix("nostr:")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            var filter: Filter
            val hintRelays = mutableSetOf<NormalizedRelayUrl>()
            var author: String? = null

            if ('@' in code) {
                // nip05 → pubkey, then fetch that author's profile metadata.
                author = ctx.requireUserHex(code)
                filter = Filter(authors = listOf(author), kinds = listOf(0), limit = 1)
            } else {
                val entity = Nip19Parser.uriToRoute(code)?.entity ?: return Output.error("bad_args", "could not decode: $codeArg")
                filter =
                    when (entity) {
                        is NEvent -> {
                            hintRelays.addAll(entity.relay)
                            author = entity.author
                            Filter(ids = listOf(entity.hex), limit = 1)
                        }
                        is NNote -> Filter(ids = listOf(entity.hex), limit = 1)
                        is NAddress -> {
                            hintRelays.addAll(entity.relay)
                            author = entity.author
                            Filter(kinds = listOf(entity.kind), authors = listOf(entity.author), tags = mapOf("d" to listOf(entity.dTag)), limit = 1)
                        }
                        is NProfile -> {
                            hintRelays.addAll(entity.relay)
                            author = entity.hex
                            Filter(authors = listOf(entity.hex), kinds = listOf(0), limit = 1)
                        }
                        is NPub -> {
                            author = entity.hex
                            Filter(authors = listOf(entity.hex), kinds = listOf(0), limit = 1)
                        }
                        else -> return Output.error("bad_args", "unsupported code for fetch: $codeArg")
                    }
            }

            // Outbox model: hint relays + the author's advertised write relays.
            val relays = (hintRelays + (author?.let { authorWriteRelays(ctx, it) } ?: emptySet())).ifEmpty { ctx.bootstrapRelays() }

            val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
            val events =
                received
                    .asSequence()
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map { Output.mapper.readTree(it.toJson()) }
                    .toList()

            Output.emit(
                mapOf(
                    "code" to codeArg,
                    "resolved_author" to author,
                    "queried_relays" to relays.map { it.url },
                    "count" to events.size,
                    "events" to events,
                ),
            )
            return 0
        }
    }

    /** The author's NIP-65 write (outbox) relays, draining their kind:10002 on a cache miss. */
    private suspend fun authorWriteRelays(
        ctx: Context,
        author: String,
    ): Set<NormalizedRelayUrl> {
        if (ctx.relaysOf(author) == null) {
            val f = Filter(authors = listOf(author), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1)
            ctx.drain(ctx.bootstrapRelays().associateWith { listOf(f) })
        }
        return ctx.relaysOf(author)?.writeRelaysNorm()?.toSet() ?: emptySet()
    }
}
