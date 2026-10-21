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
import com.vitorpamplona.amethyst.cli.CliJson
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.EventLocator
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.FetchAllResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PageRetryBackoff
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * `amy fetch [--kind …] [--author …] [--id …] [--tag …] [--since/--until TS]
 *            [--limit N] [--search TEXT] [--relay URL[,URL…]] [--timeout SECS]
 *            [--paginate]`
 *
 *   amy fetch <nevent1…|naddr1…|nprofile1…|npub1…|note1…|name@domain>
 *
 * One-shot query: open a subscription, collect everything until every relay
 * sends EOSE (or nothing has arrived for the timeout — an idle window, so a
 * relay still streaming is never cropped), then print and exit. This is the bounded
 * half of nak's `req`; the live-streaming half is `amy subscribe`.
 *
 * Two modes:
 *  - **filter mode** (flags) — assemble a filter and query `--relay`/outbox.
 *  - **code mode** (a nip19/nip05 positional) — Amethyst's outbox-model
 *    resolution: query the relay hints embedded in the code PLUS the author's
 *    NIP-65 write relays, exactly how the app downloads an event/profile from
 *    a shared link. This is nak's `fetch` (nip19-hint resolution).
 *
 * Results are deduplicated by id, sorted newest-first, capped at `--limit` (default
 * 100 — the SAME on both paths), and emitted as full event JSON under an `events`
 * array. `--limit 0` removes the cap entirely.
 *
 * By default (filter mode) a single `REQ` is drained to EOSE — so a relay that caps
 * its response (strfry's per-`REQ` `limit`, ~500) truncates the result. `--paginate`
 * (alias `--all`) instead walks each relay page-by-page on `until` cursors via the
 * multi-relay [Context.drainAllPages] path, fully draining sets larger than one
 * `REQ`. Both honor the same limit: `--limit N` returns the newest N, absent is 100,
 * and `--limit 0` is unbounded — combined with `--paginate` that drains the entire
 * filter, so mind broad filters. Code mode is always single-shot. Under `--paginate`,
 * `--limit` caps the walk: each page asks for what is still missing, and a relay that
 * refuses that limit (purplepag.es: `limit too high … (max 500)`) is re-asked at its max.
 *
 * `--paginate --limit 0` streams: each event is written as it arrives instead of being
 * held for sorting, so the walk runs in O(ids) memory however large it gets — in
 * ARRIVAL order (several relays page at once and interleave), not newest-first. Same
 * keys, same single JSON object under `--json`; `count` and `relay_errors` follow the
 * `events` array, and also close a stream that a failure cut short.
 *
 * A paged walk that delivered events and then gave up (refused, unreachable, silent past its
 * re-asks) still writes what arrived and then fails `incomplete` (exit 1); a relay that never
 * finished a plain `REQ` is reported in `relay_errors` as `timeout`.
 */
object FetchCommand {
    /** Output/paging cap for a fetch (either path) when `--limit` is omitted. */
    private const val DEFAULT_LIMIT = 100

    val USAGE: String =
        """
        |amy fetch — one-shot query: collect until EOSE, print, exit
        |
        |  fetch [--kind K[,K]] [--author U[,U]]       --author/--id accept npub/nevent/note/hex.
        |        [--id ID[,ID]] [--tag e=ID,p=PK,…]     default --limit 100 (0 = unbounded),
        |        [--since TS] [--until TS] [--limit N]  --timeout 8s.
        |        [--search TEXT] [--relay URL[,URL…]]
        |        [--timeout SECS] [--paginate|--all]    --paginate walks each relay page-by-page
        |                                                past its per-REQ cap (alias --all);
        |                                                --limit caps the walk; a relay refusing
        |                                                a big page is re-asked at its max; a
        |                                                dropped connection is resumed where the
        |                                                walk got to (reported as `resumes`); a
        |                                                walk that still stops partway prints
        |                                                what arrived and exits 1 `incomplete`.
        |                                                --paginate --limit 0 streams events in
        |                                                arrival order (not newest-first);
        |                                                --out FILE writes them there, one JSON
        |                                                per line (gzipped for .gz), and prints
        |                                                only the summary.
        |                                                A --search with words gets one page per
        |                                                relay (hits are ranked; listed in
        |                                                unpaged_relays, `amy sync` gets them all);
        |                                                key:value tokens alone (include:spam) page,
        |                                                except trust-ranked observer:<pubkey>.
        |  fetch <nevent1…|naddr1…|nprofile1…|npub1…|note1…|name@domain>
        |                                               outbox-model resolution of a shared code.
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        val args = Args(rest)
        // `--limit`: omitted → DEFAULT_LIMIT on BOTH the plain and --paginate paths;
        // `0` → unbounded (drain everything — only useful with --paginate); negative
        // → error. `effectiveLimit == null` means "no cap".
        val explicitLimit = args.flag("limit")?.toIntOrNull()
        if (explicitLimit != null && explicitLimit < 0) return Output.error("bad_args", "--limit must be >= 0 (0 = unbounded)")
        val effectiveLimit: Int? = if (explicitLimit == 0) null else (explicitLimit ?: DEFAULT_LIMIT)
        val timeoutMs = args.timeoutMs(8)
        // The filter/relay/paging flags are read later (buildFilter, queryTargets,
        // the --paginate branch) and code mode skips them entirely, so whitelist
        // them here where both paths still share the flow.
        args.rejectUnknown("kind", "author", "id", "tag", "since", "until", "search", "relay", "paginate", "all", "out")

        // Code mode: a nip19/nip05 positional resolves its own relays via the
        // outbox model rather than using a hand-built filter. It fetches a single
        // entity, so it just uses the (positive) default cap.
        args.positionalOrNull(0)?.takeIf { looksLikeCode(it) }?.let {
            return fetchByCode(dataDir, it, effectiveLimit ?: DEFAULT_LIMIT, timeoutMs)
        }

        // Carry the effective limit on the filter so both paths agree: --paginate
        // pages each relay up to it (or fully drains the filter when unbounded) and a
        // plain fetch asks the relay for that many.
        val filter = RawEventSupport.buildFilter(args).copy(limit = effectiveLimit)
        val paginate = args.bool("paginate") || args.bool("all")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val relays = RawEventSupport.queryTargets(ctx, args)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")

            val relayErrors = mutableMapOf<NormalizedRelayUrl, RelayError>()
            val unpaged = mutableSetOf<NormalizedRelayUrl>()
            val incomplete = mutableMapOf<NormalizedRelayUrl, Int>()
            val resumes = Resumes()
            val onWalkEnd = walkEnded(relayErrors, unpaged, incomplete)

            // Unbounded walk: there is no "newest N" to keep, so nothing needs sorting —
            // write each event out as it arrives instead of holding the whole history.
            val out = args.flag("out")
            if (paginate && effectiveLimit == null && out != null) return streamAllPagesToFile(ctx, filter, relays, timeoutMs, relayErrors, unpaged, incomplete, onWalkEnd, resumes, File(out))
            if (out != null) return Output.error("bad_args", "--out streams a whole walk: it needs --paginate --limit 0")
            if (paginate && effectiveLimit == null) return streamAllPages(ctx, filter, relays, timeoutMs, relayErrors, unpaged, incomplete, onWalkEnd, resumes)

            val received =
                if (paginate) {
                    ctx.drainAllPages(
                        relays.associateWith { listOf(filter) },
                        timeoutMs,
                        onRelayResult = onWalkEnd,
                        resumeBackoff = PageRetryBackoff.RESUME,
                        onRelayResume = resumes::heard,
                    )
                } else {
                    val result = ctx.drainResult(relays.associateWith { listOf(filter) }, timeoutMs)
                    relayErrors.putAll(relayErrorsOf(result))
                    result.events
                }
            val ordered =
                received
                    .asSequence()
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
            // effectiveLimit caps the output on both paths; null (--limit 0) is uncapped.
            val capped = if (effectiveLimit != null) ordered.take(effectiveLimit) else ordered
            val events =
                capped
                    .map { CliJson.parse(it.toJson()) }
                    .toList()

            return emitWithRelayErrors(
                mapOf(
                    "queried_relays" to relays.map { it.url },
                    "count" to events.size,
                    "events" to events,
                ) + unpagedField(unpaged) + resumes.field(),
                relays,
                relayErrors,
                incomplete,
            )
        }
    }

    /**
     * `--paginate --limit 0`: page every relay to the end and write each verified, deduped
     * event the moment it arrives. Holding them to sort first is what ran a 24h walk of
     * relay.libernet.app (~28k events, ~230 MB of JSON) out of a 1 GB heap; here memory is
     * the dedup set's ids. The price is order: events come out in ARRIVAL order (relay by
     * relay, each newest-first page by page), not globally newest-first. The result shape
     * is unchanged — the same keys, one JSON object on one line under `--json`, with `count`
     * and `relay_errors` written after the `events` array.
     */
    private suspend fun streamAllPages(
        ctx: Context,
        filter: Filter,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
        relayErrors: MutableMap<NormalizedRelayUrl, RelayError>,
        unpaged: MutableSet<NormalizedRelayUrl>,
        incomplete: Map<NormalizedRelayUrl, Int>,
        onWalkEnd: (NormalizedRelayUrl, PagedFetchResult) -> Unit,
        resumes: Resumes,
    ): Int {
        val stream = Output.listStream(mapOf("queried_relays" to relays.map { it.url }), "events")
        val count =
            try {
                ctx.streamAllPages(relays.associateWith { listOf(filter) }, timeoutMs, onRelayResult = onWalkEnd, resumeBackoff = PageRetryBackoff.RESUME, onRelayResume = resumes::heard) { _, event ->
                    stream.item({ event.toJson() }, { eventAsMap(event) })
                }
            } catch (e: Throwable) {
                // Close the half-written object (with an `error`) before anything else reports:
                // a second JSON object after a cut-off one is unparseable either way. The relays
                // that refused so far are part of that answer, as on the normal path.
                val errors = reportRelayErrors(relayErrors, warn = true)
                stream.abort(e.message ?: e::class.simpleName, if (errors.isEmpty()) emptyMap() else mapOf("relay_errors" to errors))
                if (e is CancellationException || !stream.started) throw e
                return 1
            }

        // Nothing arrived, so nothing was written: answer exactly as the buffered path would
        // (an empty result, or `no_relay_served` when every relay refused).
        if (!stream.started) {
            return emitWithRelayErrors(mapOf("queried_relays" to relays.map { it.url }, "count" to 0, "events" to emptyList<Any>()) + unpagedField(unpaged), relays, relayErrors)
        }

        // The incomplete error names every relay itself; a warning first would say each twice.
        val errors = reportRelayErrors(relayErrors, warn = incomplete.isEmpty())
        stream.finish(mapOf("count" to count) + unpagedField(unpaged) + resumes.field() + (if (errors.isEmpty()) emptyMap() else mapOf("relay_errors" to errors)))
        return if (incomplete.isEmpty()) 0 else incompleteError(mapOf("count" to count), relayErrors, incomplete)
    }

    /**
     * `--paginate --limit 0 --out FILE`: [streamAllPages] into a file, one event per line, the
     * shape `amy publish --file` reads back, verified but not cached. Stdout carries only the
     * summary.
     */
    private suspend fun streamAllPagesToFile(
        ctx: Context,
        filter: Filter,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
        relayErrors: MutableMap<NormalizedRelayUrl, RelayError>,
        unpaged: MutableSet<NormalizedRelayUrl>,
        incomplete: Map<NormalizedRelayUrl, Int>,
        onWalkEnd: (NormalizedRelayUrl, PagedFetchResult) -> Unit,
        resumes: Resumes,
        file: File,
    ): Int {
        val raw =
            try {
                file.outputStream().buffered(1 shl 16)
            } catch (e: IOException) {
                return Output.error("bad_args", "cannot write --out ${file.path}: ${e.message}")
            }
        val writer = (if (file.name.endsWith(".gz")) GZIPOutputStream(raw, 1 shl 16) else raw).bufferedWriter()
        var written = 0
        val count =
            try {
                writer.use { w ->
                    // Not cached: an export is the one walk too large for the local store.
                    ctx.streamAllPages(relays.associateWith { listOf(filter) }, timeoutMs, onRelayResult = onWalkEnd, cache = false, resumeBackoff = PageRetryBackoff.RESUME, onRelayResume = resumes::heard) { _, event ->
                        synchronized(w) {
                            w.write(event.toJson())
                            w.write('\n'.code)
                            written++
                        }
                    }
                }
            } catch (e: IOException) {
                // A full disk mid-export: say how far the file got, so a rerun can pick up with
                // `--until` instead of starting over.
                return Output.error("runtime", "writing ${file.path} failed: ${e.message}", mapOf("out" to file.path, "count" to written))
            }
        return emitWithRelayErrors(
            mapOf("queried_relays" to relays.map { it.url }, "count" to count, "out" to file.path) + unpagedField(unpaged) + resumes.field(),
            relays,
            relayErrors,
            incomplete,
        )
    }

    /**
     * `unpaged_relays`, for the relays whose walk ended [PagedFetchResult.End.UNPAGEABLE]: a
     * `search` filter (NIP-50 hits are ranked, so they get one page) or a relay that ignores
     * `until`. Their answer may be partial, so each gets a stderr warning pointing at `amy
     * sync`, which reconciles the whole matching set over NIP-77 instead of paging it.
     * Empty when every walk paged to its end, so the key only appears when it means something.
     */
    private fun unpagedField(unpaged: Set<NormalizedRelayUrl>): Map<String, Any> {
        val sorted = synchronized(unpaged) { unpaged.sortedBy { it.url } }
        if (sorted.isEmpty()) return emptyMap()
        sorted.forEach { relay ->
            System.err.println(
                "warning: ${relay.url} could only be read one page deep (a search filter, or the relay ignores `until`), " +
                    "so this may be partial. To download every match, run `amy sync --relay ${relay.url}` with the same filter flags.",
            )
        }
        return mapOf("unpaged_relays" to sorted.map { it.url })
    }

    /** Returns the refusing relays as the `relay_errors` map, warning about each on stderr when [warn]. */
    private fun reportRelayErrors(
        relayErrors: Map<NormalizedRelayUrl, RelayError>,
        warn: Boolean,
    ): Map<String, Map<String, Any?>> {
        val sorted = synchronized(relayErrors) { relayErrors.entries.sortedBy { it.key.url }.map { it.key to it.value } }
        if (warn) sorted.forEach { (relay, error) -> System.err.println("warning: ${relay.url} ${error.describe()}") }
        return sorted.associate { (relay, error) -> relay.url to error.toMap() }
    }

    /** An event as the map its NIP-01 JSON would parse to, for text-mode rendering. */
    private fun eventAsMap(event: Event): Map<String, Any?> =
        linkedMapOf(
            "id" to event.id,
            "pubkey" to event.pubKey,
            "created_at" to event.createdAt,
            "kind" to event.kind,
            "tags" to event.tags.map { it.toList() },
            "content" to event.content,
            "sig" to event.sig,
        )

    /**
     * The re-asks of walks a connection ended, per relay: each one said on stderr as it happens,
     * and the counts reported as `resumes` when any. The quiet re-ask that confirms an empty
     * last page is routine, so it is neither said nor counted.
     */
    private class Resumes {
        private val counts = LinkedHashMap<NormalizedRelayUrl, Int>()

        fun heard(
            relay: NormalizedRelayUrl,
            attempt: Int,
            last: PagedFetchResult,
        ) {
            if (last.end == PagedFetchResult.End.DRAINED) return
            synchronized(counts) { counts[relay] = (counts[relay] ?: 0) + 1 }
            val why = "${last.end.name.lowercase()}${last.message?.let { ": $it" }.orEmpty()}"
            System.err.println("warning: ${relay.url} ended the walk ($why). Asking again from where it got to (attempt $attempt)")
        }

        fun field(): Map<String, Any> = synchronized(counts) { if (counts.isEmpty()) emptyMap() else mapOf("resumes" to counts.entries.associate { it.key.url to it.value }) }
    }

    /**
     * Why a relay gave us nothing, in its own words: a CLOSED reason (relay.zapstore.dev
     * answers a large `limit` with `filters are too vague`), an `auth-required` refusal, or
     * the connection error. Without it an empty result looks like an empty relay.
     */
    private class RelayError(
        val reason: String,
        val message: String,
    ) {
        fun toMap() = mapOf("reason" to reason, "message" to message)

        /** "closed the request: filters are too vague", for the stderr warning. */
        fun describe(): String {
            val what =
                when (reason) {
                    CLOSED -> "closed the request"
                    AUTH_REQUIRED -> "requires authentication"
                    TIMEOUT -> "stopped answering"
                    else -> "could not be reached"
                }
            return if (message.isEmpty()) what else "$what: $message"
        }
    }

    /**
     * The `onRelayResult` hook every paged path shares: a refusal or silence goes to
     * [relayErrors], a walk read one page deep to [unpaged], and a walk that delivered events
     * before it gave up to [incomplete] with how many it delivered.
     */
    private fun walkEnded(
        relayErrors: MutableMap<NormalizedRelayUrl, RelayError>,
        unpaged: MutableSet<NormalizedRelayUrl>,
        incomplete: MutableMap<NormalizedRelayUrl, Int>,
    ): (NormalizedRelayUrl, PagedFetchResult) -> Unit =
        { relay, result ->
            val refusal = refusalOf(result)
            if (refusal != null) {
                synchronized(relayErrors) { relayErrors[relay] = refusal }
                if (result.downloaded > 0) synchronized(incomplete) { incomplete[relay] = result.downloaded }
            }
            if (result.end == PagedFetchResult.End.UNPAGEABLE) synchronized(unpaged) { unpaged.add(relay) }
        }

    /**
     * Each relay of a single-`REQ` drain that refused it or never finished it. A relay still
     * without EOSE when the idle window ran out ([FetchAllResult.stalled]) is a `timeout`: it
     * came back as an empty success before, indistinguishable from a relay that holds nothing.
     */
    private fun relayErrorsOf(result: FetchAllResult): Map<NormalizedRelayUrl, RelayError> =
        buildMap {
            result.doneReasons.forEach { (relay, reason) -> refusalOf(reason)?.let { put(relay, it) } }
            result.stalled.forEach { relay -> put(relay, RelayError(TIMEOUT, "no EOSE within --timeout")) }
        }

    /** A [FetchAllResult.doneReasons] entry as a [RelayError]; null when the relay answered. */
    private fun refusalOf(doneReason: String): RelayError? {
        val kind = doneReason.substringBefore(':')
        val message = doneReason.substringAfter(':', "").trim()
        return when (kind) {
            "closed" -> RelayError(CLOSED, message)
            "auth-refused" -> RelayError(AUTH_REQUIRED, message)
            "cannot" -> RelayError(UNREACHABLE, message)
            else -> null
        }
    }

    /**
     * A paged walk's ending as a [RelayError]; null unless the relay refused it or stopped
     * answering. Silence is reported too: a walk it ended may be missing everything below its
     * cursor, and before, a relay silent from the start came back as an empty success.
     */
    private fun refusalOf(result: PagedFetchResult): RelayError? =
        when (result.end) {
            PagedFetchResult.End.CLOSED -> RelayError(CLOSED, result.message.orEmpty())
            PagedFetchResult.End.AUTH_REQUIRED -> RelayError(AUTH_REQUIRED, result.message.orEmpty())
            PagedFetchResult.End.CANNOT_CONNECT -> RelayError(UNREACHABLE, result.message.orEmpty())
            PagedFetchResult.End.IDLE -> RelayError(TIMEOUT, "no answer within --timeout")
            else -> null
        }

    /**
     * Emits [result] plus `relay_errors` (relay URL → `{reason, message}`) and warns on
     * stderr for each of those relays that refused while another one served. When none of the queried relays served the request
     * and nothing came back, it is an error (`no_relay_served`, exit 1) rather than an
     * empty success. When a walk gave up after delivering events ([incomplete]), the result is
     * still emitted, since what arrived is good, and the fetch then fails `incomplete`.
     */
    private fun emitWithRelayErrors(
        result: Map<String, Any?>,
        relays: Set<NormalizedRelayUrl>,
        relayErrors: Map<NormalizedRelayUrl, RelayError>,
        incomplete: Map<NormalizedRelayUrl, Int> = emptyMap(),
    ): Int {
        val sorted = relayErrors.entries.sortedBy { it.key.url }
        val errors = sorted.associate { it.key.url to it.value.toMap() }

        // The error carries every reason itself; warning first would print each one twice and leave
        // `--json` stderr as more than the one error object.
        if (result["count"] == 0 && relays.isNotEmpty() && relayErrors.keys.containsAll(relays)) {
            val detail = sorted.joinToString("; ") { (relay, error) -> "${relay.url}: ${error.message.ifEmpty { error.reason }}" }
            return Output.error("no_relay_served", detail, mapOf("relay_errors" to errors))
        }

        if (incomplete.isEmpty()) sorted.forEach { (relay, error) -> System.err.println("warning: ${relay.url} ${error.describe()}") }

        Output.emit(if (errors.isEmpty()) result else result + ("relay_errors" to errors))
        return if (incomplete.isEmpty()) 0 else incompleteError(result, relayErrors, incomplete)
    }

    /**
     * `incomplete` (exit 1): a paged walk delivered events and then gave up, refused or silent
     * past its re-asks, so what was written (stdout, or `--out`) is good but not the whole
     * answer. It used to exit 0 with only a `relay_errors` entry, and a script reading the exit
     * code took a partial export for a full one. The payload repeats `count` (and `out`) so the
     * caller can resume with `--until` without parsing stdout.
     */
    private fun incompleteError(
        result: Map<String, Any?>,
        relayErrors: Map<NormalizedRelayUrl, RelayError>,
        incomplete: Map<NormalizedRelayUrl, Int>,
    ): Int {
        val partial = synchronized(incomplete) { incomplete.entries.sortedBy { it.key.url }.map { it.key to it.value } }
        val errors = synchronized(relayErrors) { relayErrors.entries.sortedBy { it.key.url }.associate { it.key.url to it.value.toMap() } }
        val detail =
            partial.joinToString("; ") { (relay, delivered) ->
                "${relay.url} ${relayErrors[relay]?.describe() ?: "ended the walk"} after $delivered events"
            }
        return Output.error(
            "incomplete",
            detail,
            mapOf(
                "count" to result["count"],
                "out" to result["out"],
                "incomplete_relays" to partial.associate { (relay, delivered) -> relay.url to delivered },
                "relay_errors" to errors,
            ),
        )
    }

    private const val CLOSED = "closed"
    private const val AUTH_REQUIRED = "auth_required"
    private const val UNREACHABLE = "unreachable"
    private const val TIMEOUT = "timeout"

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
        Context.openOrAnonymous(dataDir).use { ctx ->
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
            val relays = (hintRelays + (author?.let { EventLocator.authorOutboxRelays(NoteSupport.access(ctx), it, timeoutMs) } ?: emptySet())).ifEmpty { ctx.bootstrapRelays() }

            val drained = ctx.drainResult(relays.associateWith { listOf(filter) }, timeoutMs)
            val relayErrors = relayErrorsOf(drained)
            val events =
                drained.events
                    .asSequence()
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map { CliJson.parse(it.toJson()) }
                    .toList()

            return emitWithRelayErrors(
                mapOf(
                    "code" to codeArg,
                    "resolved_author" to author,
                    "queried_relays" to relays.map { it.url },
                    "count" to events.size,
                    "events" to events,
                ),
                relays,
                relayErrors,
            )
        }
    }
}
