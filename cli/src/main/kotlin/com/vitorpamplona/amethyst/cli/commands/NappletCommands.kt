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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent

/**
 * `amy napplet fetch` — resolve a single path of a NIP-5D napplet over
 * Nostr + Blossom with the full runtime verification contract:
 *
 *  1. fetch + signature-verify the manifest (kind 15129 root, 35129 named with
 *     `--d`, or 5129 snapshot with `--snapshot <event-id>`),
 *  2. recompute the NIP-5A aggregate hash and check it against the manifest's `x`
 *     tag — a mismatch is refused up front (`aggregate_mismatch`),
 *  3. download the path's blob from Blossom and accept only a copy whose sha256
 *     matches the per-path pin (an untrusted server cannot substitute a blob).
 *
 * It also reports the napplet's `requires` capabilities, so a shell can see which
 * NAP domains it would have to broker. Steps 2–3 live in quartz
 * (`NappletManifest.verifyAggregate`, `StaticSiteResolver`); this is thin glue,
 * shared with `amy nsite` via [StaticSiteFetch].
 */
object NappletCommands {
    val USAGE: String =
        """
        |Napplets (NIP-5D kind:5129/15129/35129):
        |  napplet fetch AUTHOR [--d ID] [--path P]    like `nsite fetch`, plus NIP-5D verification:
        |        [--server URL[,URL]] [--relay URL[,URL]]  recompute + check the `x` aggregate hash and
        |        [--out FILE] [--timeout SECS]          report the napplet's `requires` capabilities
        |        [--max-inline-bytes N]                 (--d selects a kind:35129 named napplet, else
        |  napplet fetch --snapshot EVENT-ID            the kind:15129 root; --snapshot pins a kind:5129
        |        [--path P] …                            immutable snapshot; --identifier aliases --d)
        |  napplet publish DIR --server URL[,URL]      upload a napplet directory to Blossom and
        |        [--requires identity,relay,…]           broadcast its NIP-5D manifest (kind:15129
        |        [--d ID] [--relay URL[,URL]]            root, or 35129 named with --d); adds the x
        |        [--title T] [--description D]           aggregate hash and the `requires` capability
        |        [--source URL] [--icon URL]             tags the shell gates on
        |  napplet serve AUTHOR [--d ID] [--port N]    fetch + aggregate-verify the manifest and serve
        |        [--server URL[,URL]] [--relay URL[,URL]]  its static content over local HTTP (the
        |        [--timeout SECS]                        window.napplet.* runtime needs the app host)
        |  napplet list AUTHOR [--relay URL[,URL]]     enumerate an author's napplets: the root and
        |        [--timeout SECS]                        every named one, latest per identifier
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "napplet",
            tail,
            "napplet <fetch|publish|serve|list> …",
            help = USAGE,
            routes =
                mapOf(
                    "fetch" to { rest -> fetch(dataDir, rest) },
                    "publish" to { rest -> publish(dataDir, rest) },
                    "serve" to { rest -> serve(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                ),
        )

    /**
     * `amy napplet list <author> [--relay R] [--timeout S]` — enumerate an author's published
     * napplets: the root (kind 15129) and every named one (kind 35129), latest per identifier.
     */
    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "napplet list <author> [--relay R] [--timeout S]")
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        val timeoutSecs = args.longFlag("timeout", 8L)
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val authorHex = ctx.requireUserHex(author)
            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }
            val filter = Filter(kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND), authors = listOf(authorHex))
            val items =
                ctx
                    .drain(relays.associateWith { listOf(filter) }, timeoutSecs * 1000)
                    .map { (_, ev) -> ev }
                    .filter { it.pubKey == authorHex && it is NappletManifest }
                    .groupBy { it.kind to (it as? NamedNappletEvent)?.identifier() }
                    .mapNotNull { (_, dupes) -> dupes.maxByOrNull { it.createdAt } }
                    .sortedByDescending { it.createdAt }
                    .map { ev ->
                        val m = ev as NappletManifest
                        mapOf(
                            "kind" to ev.kind,
                            "d" to (ev as? NamedNappletEvent)?.identifier(),
                            "title" to m.title(),
                            "description" to m.description(),
                            "paths" to m.paths().size,
                            "servers" to m.servers(),
                            "requires" to m.requires(),
                            "aggregate_sha256" to m.declaredAggregateHash(),
                            "aggregate_verified" to m.verifyAggregate(),
                            "event_id" to ev.id,
                            "created_at" to ev.createdAt,
                        )
                    }
            Output.emit(mapOf("pubkey" to authorHex, "count" to items.size, "napplets" to items))
            return 0
        }
    }

    /**
     * `amy napplet serve <author> [--d ID] [--port N]` — fetch + aggregate-verify the manifest and
     * serve its static content over a local HTTP server. NOTE: the napplet's window.napplet.* runtime
     * needs the Amethyst host; this serves the files only, for inspecting that they load/route.
     */
    private suspend fun serve(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "napplet serve <author> [--d ID] [--port N] [--server S] [--relay R]")
        val identifier = args.flag("d") ?: args.flag("identifier")
        val port = args.intFlag("port", 8080)
        val extraServers = StaticSiteFetch.commaList(args.flag("server"))
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        val timeoutSecs = args.longFlag("timeout", 8L)
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val authorHex = ctx.requireUserHex(author)
            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }
            val event =
                fetchByAuthor(ctx, authorHex, identifier, relays, timeoutSecs * 1000)
                    ?: return Output.error("not_found", "no napplet manifest found", mapOf("pubkey" to authorHex, "d" to identifier))
            val manifest = event as NappletManifest
            if (!manifest.verifyAggregate()) {
                return Output.error("aggregate_mismatch", "manifest x aggregate does not match its path tags")
            }
            return StaticSiteServe.serve(manifest.paths(), (manifest.servers() + extraServers).distinct(), port)
        }
    }

    /**
     * `amy napplet publish <dir> --server <blossom> [--requires identity,relay,…] [--d ID] [--relay …]`
     * — upload a napplet directory to Blossom and broadcast its NIP-5D manifest (kind 15129 root, or
     * 35129 named with `--d`). The builder adds the `x` aggregate hash and the `requires` capability
     * tags the shell gates on.
     */
    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        StaticSitePublish.run(
            dataDir,
            rest,
            "napplet publish <dir> --server <blossom-url> [--requires identity,relay,…] [--d ID] [--relay R] [--title T] [--icon URL]",
        ) { m ->
            if (m.identifier != null) {
                NamedNappletEvent.build(m.identifier, m.paths, m.servers, m.requires, m.title, m.description, m.source, m.icon)
            } else {
                RootNappletEvent.build(m.paths, m.servers, m.requires, m.title, m.description, m.source, m.icon)
            }
        }

    private suspend fun fetch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val snapshotId = args.flag("snapshot")
        val author = args.positionalOrNull(0)
        if (snapshotId == null && author == null) {
            return Output.error("bad_args", "napplet fetch <author> [--d ID] | --snapshot <event-id> [--path P]")
        }
        val identifier = args.flag("d") ?: args.flag("identifier")
        val requestPath = args.flag("path", "/")!!
        val outFile = args.flag("out")
        val timeoutSecs = args.longFlag("timeout", 8L)
        val maxInlineBytes = args.longFlag("max-inline-bytes", 65_536L)
        val extraServers = StaticSiteFetch.commaList(args.flag("server"))
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }

            val event =
                if (snapshotId != null) {
                    fetchSnapshot(ctx, snapshotId, relays, timeoutSecs * 1000)
                } else {
                    val authorHex = ctx.requireUserHex(author!!)
                    fetchByAuthor(ctx, authorHex, identifier, relays, timeoutSecs * 1000)
                }

            if (event == null) {
                return Output.error(
                    "not_found",
                    "no napplet manifest found",
                    mapOf(
                        "kind" to expectedKind(snapshotId, identifier),
                        "snapshot" to snapshotId,
                        "d" to identifier,
                    ),
                )
            }

            val manifest = event as NappletManifest

            // NIP-5D step 3: the signed aggregate MUST match the path tags. Refuse a
            // tampered/inconsistent manifest before fetching any third-party blob.
            if (!manifest.verifyAggregate()) {
                return Output.error(
                    "aggregate_mismatch",
                    "manifest x aggregate does not match its path tags",
                    mapOf(
                        "kind" to event.kind,
                        "manifest_event_id" to event.id,
                        "declared" to manifest.declaredAggregateHash(),
                        "computed" to manifest.computeAggregateHash(),
                    ),
                )
            }

            val declaredAggregate = manifest.declaredAggregateHash()
            return StaticSiteFetch.resolveAndEmit(
                requestPath = requestPath,
                paths = manifest.paths(),
                servers = (manifest.servers() + extraServers).distinct(),
                manifestFields =
                    mapOf(
                        "kind" to event.kind,
                        "manifest_event_id" to event.id,
                        "d" to identifier,
                        "requires" to manifest.requires(),
                        "aggregate_sha256" to declaredAggregate,
                        "aggregate_verified" to (declaredAggregate != null),
                    ),
                outFile = outFile,
                maxInlineBytes = maxInlineBytes,
            )
        }
    }

    private fun expectedKind(
        snapshotId: String?,
        identifier: String?,
    ): Int =
        when {
            snapshotId != null -> NappletSnapshotEvent.KIND
            identifier != null -> NamedNappletEvent.KIND
            else -> RootNappletEvent.KIND
        }

    /**
     * Fetch the latest napplet manifest for [authorHex]: a [NamedNappletEvent] (kind
     * 35129) addressed by [identifier] when `--d` is given, otherwise the author's
     * root [RootNappletEvent] (kind 15129).
     */
    private suspend fun fetchByAuthor(
        ctx: Context,
        authorHex: String,
        identifier: String?,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): Event? {
        if (relays.isEmpty()) return null
        val filter =
            if (identifier != null) {
                Filter(
                    kinds = listOf(NamedNappletEvent.KIND),
                    authors = listOf(authorHex),
                    tags = mapOf("d" to listOf(identifier)),
                    limit = 1,
                )
            } else {
                Filter(kinds = listOf(RootNappletEvent.KIND), authors = listOf(authorHex), limit = 1)
            }
        return ctx
            .drain(relays.associateWith { listOf(filter) }, timeoutMs)
            .map { (_, ev) -> ev }
            .filter { it.pubKey == authorHex && matchesIdentifier(it, identifier) }
            .maxByOrNull { it.createdAt }
    }

    /** Fetch a specific immutable snapshot ([NappletSnapshotEvent] / kind 5129) by event id. */
    private suspend fun fetchSnapshot(
        ctx: Context,
        eventId: String,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): Event? {
        if (relays.isEmpty()) return null
        val filter = Filter(ids = listOf(eventId), kinds = listOf(NappletSnapshotEvent.KIND), limit = 1)
        return ctx
            .drain(relays.associateWith { listOf(filter) }, timeoutMs)
            .map { (_, ev) -> ev }
            .firstOrNull { it is NappletSnapshotEvent && it.id == eventId }
    }

    private fun matchesIdentifier(
        event: Event,
        identifier: String?,
    ): Boolean =
        when (event) {
            is NamedNappletEvent -> event.identifier() == identifier
            is RootNappletEvent -> identifier == null
            else -> false
        }
}
