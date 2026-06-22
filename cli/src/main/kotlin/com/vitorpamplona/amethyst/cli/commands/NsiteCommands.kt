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
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.siteAggregateHash
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/**
 * `amy nsite fetch` — resolve a single path of a NIP-5A static website over
 * Nostr + Blossom, **verifying** the content against the signed manifest.
 *
 * The manifest (`RootSiteEvent` kind 15128, or `NamedSiteEvent` kind 35128 with
 * `--d`) pins each path to a sha256. This command fetches the manifest from
 * relays, then downloads the requested path's blob from the manifest's Blossom
 * servers and accepts the first copy whose recomputed sha256 matches the pin — an
 * untrusted server that substitutes or corrupts the blob is skipped.
 *
 * For NIP-5D napplets (kinds 5129/15129/35129, with aggregate-hash + capability
 * verification) use `amy napplet fetch`.
 *
 * Thin-assembly only: resolution + verification live in quartz (`StaticSiteResolver`),
 * the byte fetch in commons (`BlossomClient.download`), shared via [StaticSiteFetch].
 */
object NsiteCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "nsite",
            tail,
            "nsite <fetch|publish|serve|list> …",
            mapOf(
                "fetch" to { rest -> fetch(dataDir, rest) },
                "publish" to { rest -> publish(dataDir, rest) },
                "serve" to { rest -> serve(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
            ),
        )

    /**
     * `amy nsite list <author> [--relay R] [--timeout S]` — enumerate an author's static websites:
     * the root (kind 15128) and every named one (kind 35128), latest per identifier.
     */
    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "nsite list <author> [--relay R] [--timeout S]")
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        val timeoutSecs = args.longFlag("timeout", 8L)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val authorHex = ctx.requireUserHex(author)
            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }
            val filter = Filter(kinds = listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND), authors = listOf(authorHex))
            val items =
                ctx
                    .drain(relays.associateWith { listOf(filter) }, timeoutSecs * 1000)
                    .map { (_, ev) -> ev }
                    .filter { it.pubKey == authorHex && (it is RootSiteEvent || it is NamedSiteEvent) }
                    .groupBy { it.kind to (it as? NamedSiteEvent)?.identifier() }
                    .mapNotNull { (_, dupes) -> dupes.maxByOrNull { it.createdAt } }
                    .sortedByDescending { it.createdAt }
                    .map { ev -> describe(ev) }
            Output.emit(mapOf("pubkey" to authorHex, "count" to items.size, "nsites" to items))
            return 0
        }
    }

    private fun describe(ev: Event): Map<String, Any?> =
        when (ev) {
            is NamedSiteEvent ->
                mapOf(
                    "kind" to ev.kind,
                    "d" to ev.identifier(),
                    "title" to ev.title(),
                    "description" to ev.description(),
                    "paths" to ev.paths().size,
                    "servers" to ev.servers(),
                    "event_id" to ev.id,
                    "created_at" to ev.createdAt,
                )
            is RootSiteEvent ->
                mapOf(
                    "kind" to ev.kind,
                    "d" to null,
                    "title" to ev.title(),
                    "description" to ev.description(),
                    "paths" to ev.paths().size,
                    "servers" to ev.servers(),
                    "event_id" to ev.id,
                    "created_at" to ev.createdAt,
                )
            else -> mapOf("kind" to ev.kind, "event_id" to ev.id)
        }

    /**
     * `amy nsite serve <author> [--d ID] [--port N]` — fetch the manifest and serve it over a local
     * HTTP server (sha256-verified per request) so you can open it in a browser.
     */
    private suspend fun serve(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "nsite serve <author> [--d ID] [--port N] [--server S] [--relay R]")
        val identifier = args.flag("d")
        val port = args.intFlag("port", 8080)
        val extraServers = StaticSiteFetch.commaList(args.flag("server"))
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        val timeoutSecs = args.longFlag("timeout", 8L)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val authorHex = ctx.requireUserHex(author)
            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }
            val manifest =
                fetchManifest(ctx, authorHex, identifier, relays, timeoutSecs * 1000)
                    ?: return Output.error(
                        "not_found",
                        "no static-website manifest for this author",
                        mapOf("pubkey" to authorHex, "d" to identifier),
                    )
            return StaticSiteServe.serve(manifest.paths, (manifest.servers + extraServers).distinct(), port)
        }
    }

    /**
     * `amy nsite publish <dir> --server <blossom> [--d ID] [--relay …] [--title …]` — upload a static
     * site directory to Blossom and broadcast its NIP-5A manifest (kind 15128 root, or 35128 named
     * with `--d`). Includes the `x` aggregate hash so the manifest is self-verifying.
     */
    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        StaticSitePublish.run(
            dataDir,
            rest,
            "nsite publish <dir> --server <blossom-url> [--d ID] [--relay R] [--title T] [--description D] [--source URL] [--icon URL]",
        ) { m ->
            if (m.identifier != null) {
                NamedSiteEvent.build(m.identifier, m.paths, m.servers, m.title, m.description, m.source, m.icon) {
                    siteAggregateHash(m.paths)
                }
            } else {
                RootSiteEvent.build(m.paths, m.servers, m.title, m.description, m.source, m.icon) {
                    siteAggregateHash(m.paths)
                }
            }
        }

    private suspend fun fetch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "nsite fetch <author> [--d ID] [--path P]")
        val identifier = args.flag("d")
        val requestPath = args.flag("path", "/")!!
        val outFile = args.flag("out")
        val timeoutSecs = args.longFlag("timeout", 8L)
        val maxInlineBytes = args.longFlag("max-inline-bytes", 65_536L)
        val extraServers = StaticSiteFetch.commaList(args.flag("server"))
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val authorHex = ctx.requireUserHex(author)

            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.bootstrapRelays() }

            val manifest = fetchManifest(ctx, authorHex, identifier, relays, timeoutSecs * 1000)
            if (manifest == null) {
                return Output.error(
                    "not_found",
                    "no static-website manifest for this author",
                    mapOf(
                        "pubkey" to authorHex,
                        "kind" to if (identifier != null) NamedSiteEvent.KIND else RootSiteEvent.KIND,
                        "d" to identifier,
                    ),
                )
            }

            // Manifest servers first (author intent), then any --server fallbacks; keep order, dedupe.
            return StaticSiteFetch.resolveAndEmit(
                requestPath = requestPath,
                paths = manifest.paths,
                servers = (manifest.servers + extraServers).distinct(),
                manifestFields =
                    mapOf(
                        "kind" to manifest.kind,
                        "manifest_event_id" to manifest.id,
                        "d" to identifier,
                    ),
                outFile = outFile,
                maxInlineBytes = maxInlineBytes,
            )
        }
    }

    /**
     * Fetch the latest matching manifest from [relays]: a [NamedSiteEvent] (kind
     * 35128) addressed by [identifier] when `--d` is given, otherwise the author's
     * root [RootSiteEvent] (kind 15128).
     */
    private suspend fun fetchManifest(
        ctx: Context,
        authorHex: String,
        identifier: String?,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): SiteManifest? {
        if (relays.isEmpty()) return null
        val filter =
            if (identifier != null) {
                Filter(
                    kinds = listOf(NamedSiteEvent.KIND),
                    authors = listOf(authorHex),
                    tags = mapOf("d" to listOf(identifier)),
                    limit = 1,
                )
            } else {
                Filter(kinds = listOf(RootSiteEvent.KIND), authors = listOf(authorHex), limit = 1)
            }
        val received = ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
        return received
            .map { (_, ev) -> ev }
            .filter { it.pubKey == authorHex }
            .mapNotNull { toManifest(it, identifier) }
            .maxByOrNull { it.createdAt }
    }

    private fun toManifest(
        event: Event,
        identifier: String?,
    ): SiteManifest? =
        when (event) {
            is NamedSiteEvent ->
                if (event.identifier() == identifier) {
                    SiteManifest(event.kind, event.id, event.createdAt, event.paths(), event.servers())
                } else {
                    null
                }
            is RootSiteEvent ->
                if (identifier == null) {
                    SiteManifest(event.kind, event.id, event.createdAt, event.paths(), event.servers())
                } else {
                    null
                }
            else -> null
        }

    /**
     * Flattened view of either manifest event type (`RootSiteEvent` /
     * `NamedSiteEvent`) so the rest of the command doesn't branch on Root vs Named.
     */
    private class SiteManifest(
        val kind: Int,
        val id: String,
        val createdAt: Long,
        val paths: List<PathTag>,
        val servers: List<String>,
    )
}
