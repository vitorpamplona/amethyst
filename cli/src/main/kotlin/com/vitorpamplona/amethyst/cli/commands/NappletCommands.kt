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
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "napplet",
            tail,
            "napplet <fetch|publish> …",
            mapOf(
                "fetch" to { rest -> fetch(dataDir, rest) },
                "publish" to { rest -> publish(dataDir, rest) },
            ),
        )

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
            "napplet publish <dir> --server <blossom-url> [--requires identity,relay,…] [--d ID] [--relay R] [--title T]",
        ) { m ->
            if (m.identifier != null) {
                NamedNappletEvent.build(m.identifier, m.paths, m.servers, m.requires, m.title, m.description, m.source)
            } else {
                RootNappletEvent.build(m.paths, m.servers, m.requires, m.title, m.description, m.source)
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
        val identifier = args.flag("d")
        val requestPath = args.flag("path", "/")!!
        val outFile = args.flag("out")
        val timeoutSecs = args.longFlag("timeout", 8L)
        val maxInlineBytes = args.longFlag("max-inline-bytes", 65_536L)
        val extraServers = StaticSiteFetch.commaList(args.flag("server"))
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))

        Context.open(dataDir).use { ctx ->
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
