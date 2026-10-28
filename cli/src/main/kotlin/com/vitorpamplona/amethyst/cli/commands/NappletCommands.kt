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
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.tags.NappletIcon

/**
 * `amy napplet fetch` — resolve a NIP-5D napplet over Nostr + Blossom with the
 * runtime verification contract:
 *
 *  1. fetch + signature-verify the manifest (kind 15129 root, 35129 named with
 *     `--d`, or 5129 snapshot with `--snapshot <event-id>`),
 *  2. refuse a manifest without exactly one `x` artifact and a description
 *     (`invalid_manifest`),
 *  3. download the `/index.html` artifact from Blossom and accept only a copy whose
 *     sha256 is that `x` (an untrusted server cannot substitute it).
 *
 * It also reports the napplet's required (`R`) and optional (`O`) NAP domains, so a
 * shell can see what it would have to broker. Steps 2–3 live in quartz
 * (`NappletManifest`, `StaticSiteResolver`); this is thin glue, shared with
 * `amy nsite` via [StaticSiteFetch].
 */
object NappletCommands {
    val USAGE: String =
        """
        |Napplets (NIP-5D kind:5129/15129/35129):
        |  napplet fetch AUTHOR [--d ID] [--path P]    like `nsite fetch`, plus NIP-5D verification:
        |        [--server URL[,URL]] [--relay URL[,URL]]  verify the `/index.html` against the `x` hash and
        |        [--out FILE] [--timeout SECS]          report the napplet's R and O domains
        |        [--max-inline-bytes N]                 (--d selects a kind:35129 named napplet, else
        |  napplet fetch --snapshot EVENT-ID            the kind:15129 root; --snapshot pins a kind:5129
        |        [--path P] …                            immutable snapshot; --identifier aliases --d)
        |  napplet publish FILE --server URL[,URL]     upload one self-contained index.html to Blossom
        |        --description D [--d ID]                and broadcast its NIP-5D manifest (kind:15129
        |        [--requires identity,relay,…]           root, or 35129 named with --d): the x artifact
        |        [--optional theme,…] [--title T]        hash, the description as content, and R/O
        |        [--source URL] [--relay URL[,URL]]      domain tags; --icon takes a blob sha256
        |        [--icon SHA256 --icon-mime MIME]        and its image/png|jpeg|webp type
        |  napplet serve AUTHOR [--d ID] [--port N]    fetch + verify the manifest and serve
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
                            "valid" to m.isValid(),
                            "servers" to m.servers(),
                            "requires" to m.required(),
                            "optional" to m.optional(),
                            "artifact_sha256" to m.artifact(),
                            "event_id" to ev.id,
                            "created_at" to ev.createdAt,
                        )
                    }
            Output.emit(mapOf("pubkey" to authorHex, "count" to items.size, "napplets" to items))
            return 0
        }
    }

    /**
     * `amy napplet serve <author> [--d ID] [--port N]` — fetch + verify the manifest and
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
            val manifest = event as NappletManifest
            val artifact =
                manifest.artifactFile()?.takeIf { manifest.isValid() }
                    ?: return Output.error("invalid_manifest", "a NIP-5D manifest needs exactly one x artifact and a description")
            return StaticSiteServe.serve(listOf(artifact), (manifest.servers() + extraServers).distinct(), port)
        }
    }

    /**
     * `amy napplet publish <index.html> --server <blossom> --description D [--requires …] [--optional …] [--d ID]`
     * — upload one self-contained `index.html` to Blossom and broadcast its NIP-5D manifest (kind
     * 15129 root, or 35129 named with `--d`): the `x` artifact hash, the description as content, and
     * the `R`/`O` domain tags the shell gates on.
     */
    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        StaticSitePublish.run(
            dataDir,
            rest,
            "napplet publish <index.html> --server <blossom-url> --description D [--requires identity,relay,…] [--optional theme,…] [--d ID] [--relay R] [--title T] [--source URL] [--icon SHA256 --icon-mime MIME]",
            napplet = true,
        ) { m ->
            // run() has already checked there is one /index.html, a description and a well-formed icon.
            val artifact = m.paths.single().hash
            val description = m.description.orEmpty()
            val icon = m.icon?.let { NappletIcon(it, m.iconMime.orEmpty()) }
            if (m.identifier != null) {
                NamedNappletEvent.build(m.identifier, artifact, description, m.servers, m.requires, m.optional, icon = icon, title = m.title, source = m.source)
            } else {
                RootNappletEvent.build(artifact, description, m.servers, m.requires, m.optional, icon = icon, title = m.title, source = m.source)
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

            val manifest = event as NappletManifest

            // NIP-5D: a manifest without exactly one x artifact and a description MUST be
            // rejected; refuse it before fetching any third-party blob.
            val artifact =
                manifest.artifactFile()?.takeIf { manifest.isValid() }
                    ?: return Output.error(
                        "invalid_manifest",
                        "a NIP-5D manifest needs exactly one x artifact and a description",
                        mapOf("kind" to event.kind, "manifest_event_id" to event.id),
                    )

            return StaticSiteFetch.resolveAndEmit(
                requestPath = requestPath,
                paths = listOf(artifact),
                servers = (manifest.servers() + extraServers).distinct(),
                manifestFields =
                    mapOf(
                        "kind" to event.kind,
                        "manifest_event_id" to event.id,
                        "d" to identifier,
                        "requires" to manifest.required(),
                        "optional" to manifest.optional(),
                        "artifact_sha256" to artifact.hash,
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
