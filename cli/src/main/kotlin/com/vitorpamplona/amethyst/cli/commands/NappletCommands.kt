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
import com.vitorpamplona.amethyst.commons.napplet.NappletManifestData
import com.vitorpamplona.amethyst.commons.napplet.NappletManifestIcon
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent

/** Current NIP-5D manifest publication and signature/hash-verified artifact retrieval. */
object NappletCommands {
    val USAGE: String =
        """
        |Napplets (NIP-5D kind:5129/15129/35129):
        |  napplet fetch AUTHOR [--d ID] [--path P]    like `nsite fetch`, plus NIP-5D verification:
        |        [--server URL[,URL]] [--relay URL[,URL]]  verify the single `x` artifact hash and
        |        [--out FILE] [--timeout SECS]          report required R and optional O domains
        |        [--max-inline-bytes N]                 (--d selects a kind:35129 named napplet, else
        |  napplet fetch --snapshot EVENT-ID            the kind:15129 root; --snapshot pins a kind:5129
        |        [--path P] …                            immutable snapshot; --identifier aliases --d)
        |  napplet publish DIR --server URL[,URL]      upload one self-contained index.html to Blossom and
        |        [--requires identity,relay,…]           broadcast its NIP-5D manifest (kind:15129
        |        [--d ID] [--relay URL[,URL]]            root, or 35129 named with --d); adds the x
        |        [--title T] [--description D]           artifact hash and the R/O capability
        |        [--source URL] [--icon HASH --icon-mime MIME]             tags the shell gates on
        |        [--optional theme,…] [--archetypes feed,…]
        |        [--intents "napplet:feed/edit filters relays"]
        |  napplet serve AUTHOR [--d ID] [--port N]    fetch + artifact-verify the manifest and serve
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
                    .mapNotNull { ev ->
                        val m = NappletManifestData.parse(ev) ?: return@mapNotNull null
                        mapOf(
                            "kind" to ev.kind,
                            "d" to (ev as? NamedNappletEvent)?.identifier(),
                            "title" to m.title,
                            "description" to m.description,
                            "paths" to m.paths.size,
                            "servers" to m.servers,
                            "requires" to m.required,
                            "optional" to m.optional,
                            "artifact_sha256" to m.artifactHash,
                            "event_id" to ev.id,
                            "created_at" to ev.createdAt,
                        )
                    }
            Output.emit(mapOf("pubkey" to authorHex, "count" to items.size, "napplets" to items))
            return 0
        }
    }

    /**
     * `amy napplet serve <author> [--d ID] [--port N]` — fetch + artifact-verify the manifest and
     * serve its static content over a local HTTP server. NOTE: the napplet's window.napplet.* runtime
     * needs the Amethyst host; this serves the files only, for inspecting that they load/route.
     */
    private suspend fun serve(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val author = args.positionalOrNull(0) ?: return Output.error("bad_args", "napplet serve <author> [--d ID] [--port N] [--server S] [--relay R]")
        val identifierAlias = args.flag("identifier")
        val identifier = args.flag("d") ?: identifierAlias
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
            val manifest =
                NappletManifestData.parse(event)
                    ?: return Output.error("invalid_manifest", "Invalid current NIP-5D manifest")
            return StaticSiteServe.serve(manifest.paths, (manifest.servers + extraServers).distinct(), port)
        }
    }

    /**
     * `amy napplet publish <dir> --server <blossom> [--requires identity,relay,…] [--d ID] [--relay …]`
     * — upload one self-contained index.html to Blossom and broadcast its NIP-5D manifest (kind 15129 root, or
     * 35129 named with `--d`). The builder adds the `x` artifact hash and the R/O capability
     * tags the shell gates on.
     */
    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        StaticSitePublish.run(
            dataDir,
            rest,
            "napplet publish <dir> --server <blossom-url> [--requires identity,relay,…] [--d ID] [--relay R] [--title T] [--description D] [--icon HASH --icon-mime MIME]",
            napplet = true,
        ) { m ->
            NappletManifestData.build(
                artifactHash = m.paths.single().hash,
                description = requireNotNull(m.description),
                identifier = m.identifier,
                required = m.requires,
                optional = m.optional,
                servers = m.servers,
                title = m.title,
                source = m.source,
                icon = m.icon?.let { NappletManifestIcon(it, requireNotNull(m.iconMime)) },
                archetypes = m.archetypes,
                intents = m.intents,
            )
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
        val identifierAlias = args.flag("identifier")
        val identifier = args.flag("d") ?: identifierAlias
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

            val manifest =
                NappletManifestData.parse(event)
                    ?: return Output.error("invalid_manifest", "Invalid current NIP-5D manifest")
            return StaticSiteFetch.resolveAndEmit(
                requestPath = requestPath,
                paths = manifest.paths,
                servers = (manifest.servers + extraServers).distinct(),
                manifestFields =
                    mapOf(
                        "kind" to event.kind,
                        "manifest_event_id" to event.id,
                        "d" to identifier,
                        "requires" to manifest.required,
                        "optional" to manifest.optional,
                        "artifact_sha256" to manifest.artifactHash,
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
