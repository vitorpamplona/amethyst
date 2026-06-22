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
            "nsite <fetch|publish> …",
            mapOf(
                "fetch" to { rest -> fetch(dataDir, rest) },
                "publish" to { rest -> publish(dataDir, rest) },
            ),
        )

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
            "nsite publish <dir> --server <blossom-url> [--d ID] [--relay R] [--title T] [--description D] [--source URL]",
        ) { m ->
            if (m.identifier != null) {
                NamedSiteEvent.build(m.identifier, m.paths, m.servers, m.title, m.description, m.source) {
                    siteAggregateHash(m.paths)
                }
            } else {
                RootSiteEvent.build(m.paths, m.servers, m.title, m.description, m.source) {
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
