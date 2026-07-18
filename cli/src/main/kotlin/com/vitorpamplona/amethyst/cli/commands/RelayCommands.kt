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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayProber
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `amy relay …` — manage every relay list this account maintains, mirroring
 * Amethyst's relay-settings screen. The structure is noun-first, matching the
 * rest of amy (`marmot group create`, `cashu mint ping`): one relay-list bucket
 * per noun, with `add`/`remove`/`set`/`clear` sub-verbs (a bare noun lists it).
 *
 * ```
 * relay outbox|inbox|nip65 …          NIP-65 kind:10002 (see below)
 * relay dm …                          kind:10050  NIP-17 DM inbox
 * relay key-package …                 kind:10051  MIP-00 KeyPackage relays
 * relay search …                      kind:10007  NIP-50 search relays
 * relay private …                     kind:10013  NIP-37 private outbox (encrypted)
 * relay blocked|trusted|proxy|        kind:10006/10089/10087/
 *       indexer|broadcast|feeds …          10086/10088/10012 (NIP-51, encrypted)
 * relay add|remove URL                fan-out to the transport lists (nip65+dm+key-package)
 * relay list                          overview of every bucket
 * relay publish-lists                 broadcast every configured list
 * relay info URL                      NIP-11 document fetch (stateless)
 * ```
 *
 * # NIP-65 read/write markers
 *
 * kind:10002 stores one entry per relay with a read/write marker. amy fronts it
 * with two facet-nouns — `outbox` (write) and `inbox` (read) — that edit the one
 * event while honouring the merge rules from the spec:
 *
 *  - `outbox add R` when R is already read-only  → R becomes **both**.
 *  - `inbox  add R` when R is already write-only  → R becomes **both**.
 *  - `outbox remove R` when R is **both**         → R stays as **read** (inbox).
 *  - `inbox  remove R` when R is **both**         → R stays as **write** (outbox).
 *  - removing the last remaining facet drops R from the list entirely.
 *
 * `relay nip65` shows the combined read/write view; `relay nip65 remove R` drops
 * R regardless of marker and `relay nip65 clear` wipes the whole event.
 *
 * Edits are local-first: they build, sign, and ingest the new list event into
 * the local store but do not broadcast — run `relay publish-lists` to push them.
 */
object RelayCommands {
    private const val SHORT_USAGE =
        "relay <outbox|inbox|nip65|dm|key-package|search|private|blocked|trusted|proxy|indexer|broadcast|feeds|add|remove|list|publish-lists|info|probe> …"

    val USAGE: String =
        """
        |Relays: `relay NOUN [add|remove|set|clear|list] …` (bare NOUN lists it)
        |  NOUN = outbox|inbox|nip65 (kind:10002)  dm (10050)  key-package (10051)
        |         search (10007)  private (10013)  blocked|trusted|proxy|indexer|
        |         broadcast|feeds (NIP-51, encrypted)
        |  relay outbox add URL          add URL as a write relay (read-only → both)
        |  relay inbox add URL           add URL as a read relay (write-only → both)
        |  relay outbox remove URL       drop write (both → read; write-only → gone)
        |  relay blocked add URL         e.g. private lists: add/remove/set/clear
        |  relay nip65                   show the combined read/write list
        |  relay nip65 remove|clear      drop one relay regardless of marker / wipe the event
        |  relay add URL                 fan-out: nip65(both)+dm+key-package
        |  relay remove URL              fan-out remove from those three
        |  relay list                    print every configured relay bucket
        |  relay publish-lists           broadcast every configured relay list
        |  relay info URL                fetch + print a relay's NIP-11 info document (stateless)
        |  relay probe [--timeout SECS]  relay census: mass-connect every relay the store
        |    [--concurrency N]            knows and record live/dead + measured rtt-open
        |                                 into the reachability cache (NIP-66 kind:30166),
        |                                 so reachability-aware commands (graperank crawl/
        |                                 refresh) skip dead relays and wait once
        |                                 (--timeout: per wave, default 15s)
        """.trimMargin()

    // ------------------------------------------------------------------
    // Flat buckets — a plain list of relay URLs, one Nostr replaceable kind.
    // ------------------------------------------------------------------

    private class Flat(
        /** Command noun (kebab-case) and its aliases. */
        val noun: String,
        /** Key used in the `relay list` overview JSON (snake_case). */
        val jsonKey: String,
        val kind: Int,
        val aliases: Set<String>,
        val read: suspend (Context, HexKey) -> List<NormalizedRelayUrl>,
        val build: suspend (Context, List<NormalizedRelayUrl>) -> Event,
    ) {
        fun matches(token: String) = token == noun || token in aliases
    }

    private val FLATS: List<Flat> =
        listOf(
            Flat(
                "dm",
                "dm",
                ChatMessageRelayListEvent.KIND,
                setOf("chat", "inbox-dm"),
                read = { c, pk -> c.dmInboxOf(pk)?.relays().orEmpty() },
                build = { c, r -> ChatMessageRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "key-package",
                "key_package",
                KeyPackageRelayListEvent.KIND,
                setOf("keypackage", "key_package"),
                read = { c, pk -> c.keyPackageRelaysOf(pk)?.relays().orEmpty() },
                build = { c, r -> KeyPackageRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "search",
                "search",
                SearchRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, SearchRelayListEvent.KIND) as? SearchRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> SearchRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "private",
                "private",
                PrivateOutboxRelayListEvent.KIND,
                setOf("private-outbox", "private_outbox", "nip37"),
                read = { c, pk -> (c.latestReplaceable(pk, PrivateOutboxRelayListEvent.KIND) as? PrivateOutboxRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> PrivateOutboxRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "blocked",
                "blocked",
                BlockedRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, BlockedRelayListEvent.KIND) as? BlockedRelayListEvent)?.relays(c.signer).orEmpty() },
                build = { c, r -> BlockedRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "trusted",
                "trusted",
                TrustedRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, TrustedRelayListEvent.KIND) as? TrustedRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> TrustedRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "proxy",
                "proxy",
                ProxyRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, ProxyRelayListEvent.KIND) as? ProxyRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> ProxyRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "indexer",
                "indexer",
                IndexerRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, IndexerRelayListEvent.KIND) as? IndexerRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> IndexerRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "broadcast",
                "broadcast",
                BroadcastRelayListEvent.KIND,
                emptySet(),
                read = { c, pk -> (c.latestReplaceable(pk, BroadcastRelayListEvent.KIND) as? BroadcastRelayListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> BroadcastRelayListEvent.create(r, c.signer) },
            ),
            Flat(
                "feeds",
                "feeds",
                RelayFeedsListEvent.KIND,
                setOf("favorites", "relay_feeds"),
                read = { c, pk -> (c.latestReplaceable(pk, RelayFeedsListEvent.KIND) as? RelayFeedsListEvent)?.decryptRelays(c.signer).orEmpty() },
                build = { c, r -> RelayFeedsListEvent.create(r, c.signer) },
            ),
        )

    private fun flatFor(token: String): Flat? = FLATS.firstOrNull { it.matches(token) }

    /** The two facet-nouns that edit the read/write markers of kind:10002. */
    private enum class Facet(
        val noun: String,
    ) {
        OUTBOX("outbox"),
        INBOX("inbox"),
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", SHORT_USAGE)
        val head = tail[0]
        if (head == "--help" || head == "-h" || head == "help") {
            System.err.println(USAGE)
            return 0
        }
        val rest = tail.drop(1).toTypedArray()
        return when (head) {
            "list" -> listAll(dataDir)
            "publish-lists" -> publishLists(dataDir)
            // `info` is also intercepted in Main before account resolution
            // (it needs no account); routed here too for when one exists.
            "info" -> info(rest)
            "probe" -> probe(dataDir, rest)
            "add" -> fanOut(dataDir, Args(rest), add = true)
            "remove", "rm" -> fanOut(dataDir, Args(rest), add = false)
            "outbox" -> facetVerb(dataDir, Facet.OUTBOX, rest)
            "inbox" -> facetVerb(dataDir, Facet.INBOX, rest)
            "nip65" -> nip65Verb(dataDir, rest)
            else -> {
                val flat = flatFor(head) ?: return Output.error("bad_args", "unknown relay noun: $head ($SHORT_USAGE)")
                flatVerb(dataDir, flat, rest)
            }
        }
    }

    // ------------------------------------------------------------------
    // relay info URL — stateless NIP-11 fetch
    // ------------------------------------------------------------------

    fun info(rest: Array<String>): Int {
        val args = Args(rest)
        args.rejectUnknown()
        val raw = args.positional(0, "relay-url")
        val normalized =
            raw.normalizeRelayUrlOrNull()
                ?: return Output.invalidRelayUrl(raw)
        val httpUrl = normalized.toHttp()

        val request =
            Request
                .Builder()
                .url(httpUrl)
                .header("Accept", Nip11RelayInformation.CONTENT_TYPE)
                .get()
                .build()

        return try {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Output.error("http_error", "relay returned HTTP ${response.code} for $httpUrl")
                }
                val body = response.body.string()
                val relayInfo = Nip11RelayInformation.fromJson(body)
                Output.emit(
                    mapOf(
                        "relay" to normalized.url,
                        "url" to httpUrl,
                        "info" to Output.mapper.readTree(relayInfo.toJson()),
                    ),
                )
                0
            }
        } catch (e: Exception) {
            Output.error("http_error", "could not fetch NIP-11 from $httpUrl: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // relay probe — the relay census (feeds the NIP-66 reachability cache)
    // ------------------------------------------------------------------

    /**
     * `amy relay probe [--timeout SECS] [--concurrency N]` —
     * the relay census. Mass-connects the ENTIRE relay universe the local store knows
     * (every relay advertised in any stored kind:10002, deduped per host, plus
     * everything already in the reachability cache) in parallel waves with a no-op
     * REQ, so the "is this relay alive, and how slow?" wait is paid once, up front,
     * concurrently — then records per-relay verdicts with real measured `rtt-open`
     * into the NIP-66 reachability cache (kind:30166).
     *
     * Every reachability-aware command reads that cache to skip the dead set without
     * dialing it: `graperank crawl` also pre-connects the live set in one storm,
     * separating "working but slow" (kept; the crawler's patient park path waits for
     * them) from "not working" (skipped entirely). Typical flow the first time:
     * `graperank crawl --max-hops 2` (cheap, saves the relay lists) → `relay probe`
     * → full `graperank crawl`. (`graperank probe` is kept as an alias.)
     */
    suspend fun probe(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        // Per probe WAVE, not per relay or total — a wave's stragglers are cut off
        // together when it elapses.
        val timeoutMs = args.longFlag("timeout", 15L) * 1000
        // Relays dialed at once; --relay-concurrency accepted as the alias the
        // graperank verbs spell it with.
        val waveSize = args.intFlag("concurrency", args.intFlag("relay-concurrency", Context.defaultPreconnectCap))
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val cached = ctx.reachability.snapshot()
            val universe = RelayProber.knownRelayUniverse(ctx.store) + cached.live + cached.dead
            if (universe.isEmpty()) {
                Output.emit(
                    linkedMapOf<String, Any?>(
                        "probed" to 0,
                        "note" to "no relays known locally — run `amy graperank crawl` first to gather kind:10002 relay lists",
                    ),
                )
                return 0
            }

            System.err.println(
                "[relay-probe] probing ${universe.size} relays in waves of $waveSize " +
                    "(${timeoutMs / 1000}s per wave; open-files limit ${Context.maxFileDescriptors})",
            )
            val result =
                RelayProber(ctx.client) { System.err.println(it) }
                    .probe(universe, timeoutMs, waveSize)

            ctx.reachability.recordProbed(result.reachableRttMs(), result.deadRelays())

            val rtts =
                result.reachable
                    .map { it.rttOpenMs }
                    .filter { it >= 0 }
                    .sorted()

            fun pct(p: Int): Long? = if (rtts.isEmpty()) null else rtts[(rtts.size - 1) * p / 100]
            val slowest =
                result.reachable
                    .filter { it.rttOpenMs >= 0 }
                    .sortedByDescending { it.rttOpenMs }
                    .take(10)
                    .map { mapOf("relay" to it.relay.url, "rtt_open_ms" to it.rttOpenMs) }
            val authWalled = result.reachable.count { it.error?.startsWith("closed:") == true }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "probed" to result.verdicts.size,
                    "reachable" to result.reachable.size,
                    "dead" to result.dead.size,
                    "closed_by_policy" to authWalled,
                    "elapsed_ms" to result.elapsedMs,
                    "rtt_open_p50_ms" to pct(50),
                    "rtt_open_p90_ms" to pct(90),
                    "rtt_open_p99_ms" to pct(99),
                    "slowest" to slowest,
                ),
            )
        }
        return 0
    }

    // ------------------------------------------------------------------
    // Flat-bucket verbs
    // ------------------------------------------------------------------

    private suspend fun flatVerb(
        dataDir: DataDir,
        flat: Flat,
        rest: Array<String>,
    ): Int {
        val verb = rest.getOrNull(0) ?: "list"
        val args = Args(rest.drop(1).toTypedArray())
        args.rejectUnknown()
        if (verb !in VERBS) return Output.error("bad_args", "relay ${flat.noun} $verb ($VERB_LIST)")

        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            when (verb) {
                "add" -> {
                    val url = urlArg(args) ?: return Output.invalidRelayUrl(args.positional(0, "url"))
                    val existing = flat.read(ctx, self)
                    val added = existing.none { it.url == url.url }
                    if (added) ctx.verifyAndStore(flat.build(ctx, existing + url))
                    Output.emit(mapOf("noun" to flat.noun, "kind" to flat.kind, "url" to url.url, "added" to added))
                }
                "remove", "rm" -> {
                    val url = urlArg(args) ?: return Output.invalidRelayUrl(args.positional(0, "url"))
                    val existing = flat.read(ctx, self)
                    val removed = existing.any { it.url == url.url }
                    if (removed) ctx.verifyAndStore(flat.build(ctx, existing.filterNot { it.url == url.url }))
                    Output.emit(mapOf("noun" to flat.noun, "kind" to flat.kind, "url" to url.url, "removed" to removed))
                }
                "set" -> {
                    val relays = parseUrls(args.positional) ?: return badUrlIn(args.positional)
                    if (relays.isEmpty()) return Output.error("bad_args", "set needs at least one URL; use `relay ${flat.noun} clear` to empty it")
                    val signed = flat.build(ctx, relays)
                    ctx.verifyAndStore(signed)
                    Output.emit(mapOf("noun" to flat.noun, "kind" to flat.kind, "event_id" to signed.id, "relays" to relays.map { it.url }))
                }
                "clear" -> {
                    val signed = flat.build(ctx, emptyList())
                    ctx.verifyAndStore(signed)
                    Output.emit(mapOf("noun" to flat.noun, "kind" to flat.kind, "event_id" to signed.id, "relays" to emptyList<String>()))
                }
                "list" -> Output.emit(mapOf("noun" to flat.noun, "kind" to flat.kind, "relays" to flat.read(ctx, self).map { it.url }))
            }
            return 0
        }
    }

    // ------------------------------------------------------------------
    // NIP-65 facet verbs (outbox / inbox)
    // ------------------------------------------------------------------

    private suspend fun facetVerb(
        dataDir: DataDir,
        facet: Facet,
        rest: Array<String>,
    ): Int {
        val verb = rest.getOrNull(0) ?: "list"
        val args = Args(rest.drop(1).toTypedArray())
        args.rejectUnknown()
        if (verb !in VERBS) return Output.error("bad_args", "relay ${facet.noun} $verb ($VERB_LIST)")

        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            when (verb) {
                "add", "remove", "rm" -> {
                    val present = verb == "add"
                    val url = urlArg(args) ?: return Output.invalidRelayUrl(args.positional(0, "url"))
                    val changed = mutateNip65(ctx, self) { applyFacet(it, url, facet, present) }
                    Output.emit(
                        mapOf(
                            "noun" to facet.noun,
                            "kind" to AdvertisedRelayListEvent.KIND,
                            "url" to url.url,
                            (if (present) "added" else "removed") to changed,
                            "marker" to markerLabel(readNip65(ctx, self), url),
                        ),
                    )
                }
                "set", "clear" -> {
                    val relays =
                        if (verb == "clear") {
                            emptyList()
                        } else {
                            val parsed = parseUrls(args.positional) ?: return badUrlIn(args.positional)
                            if (parsed.isEmpty()) return Output.error("bad_args", "set needs at least one URL; use `relay ${facet.noun} clear` to empty it")
                            parsed
                        }
                    mutateNip65(ctx, self) { facetSet(it, relays, facet) }
                    Output.emit(mapOf("noun" to facet.noun, "kind" to AdvertisedRelayListEvent.KIND, "relays" to facetUrls(ctx, self, facet)))
                }
                "list" -> Output.emit(mapOf("noun" to facet.noun, "kind" to AdvertisedRelayListEvent.KIND, "relays" to facetUrls(ctx, self, facet)))
            }
            return 0
        }
    }

    /** `relay nip65 …` — combined read/write view; edits limited to remove/clear. */
    private suspend fun nip65Verb(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val verb = rest.getOrNull(0) ?: "list"
        val args = Args(rest.drop(1).toTypedArray())
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            when (verb) {
                "list" -> {
                    val nip65 = ctx.relaysOf(self)
                    Output.emit(
                        mapOf(
                            "noun" to "nip65",
                            "kind" to AdvertisedRelayListEvent.KIND,
                            "read" to (nip65?.readRelaysNorm()?.map { it.url } ?: emptyList<String>()),
                            "write" to (nip65?.writeRelaysNorm()?.map { it.url } ?: emptyList<String>()),
                            "relays" to (nip65?.relaysNorm()?.map { it.url } ?: emptyList<String>()),
                        ),
                    )
                }
                "remove", "rm" -> {
                    val url = urlArg(args) ?: return Output.invalidRelayUrl(args.positional(0, "url"))
                    val removed = mutateNip65(ctx, self) { infos -> infos.filterNot { it.relayUrl.url == url.url } }
                    Output.emit(mapOf("noun" to "nip65", "kind" to AdvertisedRelayListEvent.KIND, "url" to url.url, "removed" to removed))
                }
                "clear" -> {
                    mutateNip65(ctx, self) { emptyList() }
                    Output.emit(mapOf("noun" to "nip65", "kind" to AdvertisedRelayListEvent.KIND, "relays" to emptyList<String>()))
                }
                "add", "set" ->
                    return Output.error(
                        "bad_args",
                        "nip65 carries read/write markers — use `relay outbox $verb` (write) or `relay inbox $verb` (read)",
                    )
                else -> return Output.error("bad_args", "relay nip65 $verb (list|remove|clear)")
            }
            return 0
        }
    }

    // ------------------------------------------------------------------
    // relay add/remove URL — fan-out to the transport lists
    // ------------------------------------------------------------------

    private suspend fun fanOut(
        dataDir: DataDir,
        args: Args,
        add: Boolean,
    ): Int {
        args.rejectUnknown()
        val url = urlArg(args) ?: return Output.invalidRelayUrl(args.positional(0, "url"))
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            val changed = linkedMapOf<String, Boolean>()

            // nip65 as read+write (both).
            changed["nip65"] =
                if (add) {
                    mutateNip65(ctx, self) { applyFacet(applyFacet(it, url, Facet.OUTBOX, true), url, Facet.INBOX, true) }
                } else {
                    mutateNip65(ctx, self) { infos -> infos.filterNot { it.relayUrl.url == url.url } }
                }

            for (noun in listOf("dm", "key-package")) {
                val flat = flatFor(noun)!!
                val existing = flat.read(ctx, self)
                changed[flat.jsonKey] =
                    if (add) {
                        val doAdd = existing.none { it.url == url.url }
                        if (doAdd) ctx.verifyAndStore(flat.build(ctx, existing + url))
                        doAdd
                    } else {
                        val doRemove = existing.any { it.url == url.url }
                        if (doRemove) ctx.verifyAndStore(flat.build(ctx, existing.filterNot { it.url == url.url }))
                        doRemove
                    }
            }

            val hit = changed.filterValues { it }.keys.toList()
            val miss = changed.filterValues { !it }.keys.toList()
            Output.emit(
                if (add) {
                    mapOf("url" to url.url, "added_to" to hit, "already_present" to miss)
                } else {
                    mapOf("url" to url.url, "removed_from" to hit, "not_present" to miss)
                },
            )
            return 0
        }
    }

    // ------------------------------------------------------------------
    // relay list / publish-lists
    // ------------------------------------------------------------------

    private suspend fun listAll(dataDir: DataDir): Int {
        Context.open(dataDir).use { ctx ->
            val self = ctx.identity.pubKeyHex
            val nip65 = ctx.relaysOf(self)
            val out = linkedMapOf<String, Any?>()
            out["outbox"] = nip65?.writeRelaysNorm()?.map { it.url } ?: emptyList<String>()
            out["inbox"] = nip65?.readRelaysNorm()?.map { it.url } ?: emptyList<String>()
            out["nip65"] = nip65?.relaysNorm()?.map { it.url } ?: emptyList<String>()
            for (flat in FLATS) {
                out[flat.jsonKey] = flat.read(ctx, self).map { it.url }
            }
            Output.emit(out)
            return 0
        }
    }

    private suspend fun publishLists(dataDir: DataDir): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex

            val events = linkedMapOf<String, Event?>()
            events["nip65"] = ctx.relaysOf(self)
            for (flat in FLATS) {
                events[flat.jsonKey] = ctx.latestReplaceable(self, flat.kind)
            }

            if (events.values.all { it == null }) {
                return Output.error(
                    "no_relays",
                    "no relay lists in the local store; run `amy relay add` first or `amy create` to bootstrap defaults",
                )
            }

            val targets = ctx.anyRelays()
            val eventIds = linkedMapOf<String, String?>()
            val acceptedBy = linkedMapOf<String, List<String>>()
            for ((key, event) in events) {
                eventIds[key] = event?.id
                val result = event?.let { ctx.publish(it, targets) }.orEmpty()
                acceptedBy[key] = result.filterValues { it }.keys.map { it.url }
            }

            Output.emit(mapOf("event_ids" to eventIds, "published_to" to acceptedBy))
            return 0
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private val VERBS = setOf("add", "remove", "rm", "set", "clear", "list")
    private const val VERB_LIST = "add|remove|set|clear|list"

    private fun parseUrl(raw: String): NormalizedRelayUrl? = raw.normalizeRelayUrlOrNull()

    /** The single relay-URL argument every add/remove verb takes, or null if it doesn't parse. */
    private fun urlArg(args: Args): NormalizedRelayUrl? = parseUrl(args.positional(0, "url"))

    /** Normalize + dedupe (order-preserving) a list of raw URLs, or null on any bad one. */
    private fun parseUrls(raws: List<String>): List<NormalizedRelayUrl>? {
        val out = mutableListOf<NormalizedRelayUrl>()
        for (raw in raws) out.add(raw.normalizeRelayUrlOrNull() ?: return null)
        return out.distinctBy { it.url }
    }

    /** Error exit naming the first URL in [raws] that made [parseUrls] fail. */
    private fun badUrlIn(raws: List<String>): Int = Output.invalidRelayUrl(raws.first { parseUrl(it) == null })

    private suspend fun readNip65(
        ctx: Context,
        self: HexKey,
    ): List<AdvertisedRelayInfo> = ctx.relaysOf(self)?.relays().orEmpty()

    /** Read/write flags for one relay, split out from [AdvertisedRelayType]. */
    private data class RW(
        val read: Boolean,
        val write: Boolean,
    )

    private fun AdvertisedRelayType.rw() = RW(isRead(), isWrite())

    private fun RW.toTypeOrNull(): AdvertisedRelayType? =
        when {
            read && write -> AdvertisedRelayType.BOTH
            read -> AdvertisedRelayType.READ
            write -> AdvertisedRelayType.WRITE
            else -> null
        }

    /**
     * Toggle one [facet] on/off for [url] within the kind:10002 entry list,
     * applying the NIP-65 merge rules: turning a facet on merges into `both`
     * when the other facet is set; turning the last facet off drops the relay.
     * Order is preserved.
     */
    private fun applyFacet(
        infos: List<AdvertisedRelayInfo>,
        url: NormalizedRelayUrl,
        facet: Facet,
        present: Boolean,
    ): List<AdvertisedRelayInfo> {
        val urls = LinkedHashMap<String, NormalizedRelayUrl>()
        val flags = LinkedHashMap<String, RW>()
        for (i in infos) {
            urls[i.relayUrl.url] = i.relayUrl
            flags[i.relayUrl.url] = i.type.rw()
        }
        val cur = flags[url.url] ?: RW(read = false, write = false)
        val next = if (facet == Facet.OUTBOX) cur.copy(write = present) else cur.copy(read = present)
        if (next.read || next.write) {
            urls[url.url] = url
            flags[url.url] = next
        } else {
            urls.remove(url.url)
            flags.remove(url.url)
        }
        return flags.entries.map { AdvertisedRelayInfo(urls[it.key]!!, it.value.toTypeOrNull()!!) }
    }

    /** Make exactly [targets] carry [facet], demoting/removing any relay that currently does but shouldn't. */
    private fun facetSet(
        infos: List<AdvertisedRelayInfo>,
        targets: List<NormalizedRelayUrl>,
        facet: Facet,
    ): List<AdvertisedRelayInfo> {
        val keep = targets.map { it.url }.toSet()
        var result = infos
        for (i in infos) {
            val has = if (facet == Facet.OUTBOX) i.type.isWrite() else i.type.isRead()
            if (has && i.relayUrl.url !in keep) result = applyFacet(result, i.relayUrl, facet, present = false)
        }
        for (u in targets) result = applyFacet(result, u, facet, present = true)
        return result
    }

    /** Read, transform, and (only if it changed) re-sign + store the kind:10002. Returns whether it changed. */
    private suspend fun mutateNip65(
        ctx: Context,
        self: HexKey,
        transform: (List<AdvertisedRelayInfo>) -> List<AdvertisedRelayInfo>,
    ): Boolean {
        val before = readNip65(ctx, self)
        val after = transform(before)
        if (infoKey(before) == infoKey(after)) return false
        ctx.verifyAndStore(AdvertisedRelayListEvent.create(after, ctx.signer))
        return true
    }

    private fun infoKey(list: List<AdvertisedRelayInfo>) = list.map { it.relayUrl.url to it.type }

    private suspend fun facetUrls(
        ctx: Context,
        self: HexKey,
        facet: Facet,
    ): List<String> {
        val nip65 = ctx.relaysOf(self)
        val urls = if (facet == Facet.OUTBOX) nip65?.writeRelaysNorm() else nip65?.readRelaysNorm()
        return urls?.map { it.url } ?: emptyList()
    }

    private fun markerLabel(
        infos: List<AdvertisedRelayInfo>,
        url: NormalizedRelayUrl,
    ): String =
        when (infos.firstOrNull { it.relayUrl.url == url.url }?.type) {
            AdvertisedRelayType.BOTH -> "both"
            AdvertisedRelayType.READ -> "read"
            AdvertisedRelayType.WRITE -> "write"
            null -> "none"
        }
}
