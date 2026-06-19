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
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import java.io.File

/**
 * `amy nsite fetch` — resolve a single path of a NIP-5A static website / napplet
 * over Nostr + Blossom, **verifying** the content against the signed manifest.
 *
 * The manifest (`RootSiteEvent` kind 15128, or `NamedSiteEvent` kind 35128 with
 * `--d`) pins each path to a sha256. This command fetches the manifest from
 * relays, then downloads the requested path's blob from the manifest's Blossom
 * servers and accepts the first copy whose recomputed sha256 matches the pin — an
 * untrusted server that substitutes or corrupts the blob is skipped. This is the
 * same trust boundary a napplet shell relies on, exercised end-to-end so the
 * resolver can be checked against real-world manifests (interop / agents).
 *
 * Thin-assembly only: all resolution + verification lives in quartz
 * (`StaticSiteResolver`) and the byte fetch in commons (`BlossomClient.download`).
 */
object NsiteCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "nsite <fetch> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "fetch" -> fetch(dataDir, rest)
            else -> Output.error("bad_args", "nsite ${tail[0]}")
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
        val extraServers = commaList(args.flag("server"))
        val extraRelays = commaList(args.flag("relay"))

        val ctx = Context.open(dataDir)
        try {
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

            val paths = manifest.paths
            // Manifest servers first (author intent), then any --server fallbacks; keep order, dedupe.
            val servers = (manifest.servers + extraServers).distinct()
            if (servers.isEmpty()) {
                return Output.error("no_servers", "manifest lists no Blossom servers; pass --server URL")
            }

            val blossom = BlossomClient()
            val resolution =
                StaticSiteResolver.resolve(
                    requestPath = requestPath,
                    paths = paths,
                    servers = servers,
                    fetch = { url -> blossom.download(url) },
                )

            return when (resolution) {
                is StaticSiteResolution.PathNotInManifest ->
                    Output.error(
                        "path_not_found",
                        "manifest declares no such path",
                        mapOf(
                            "path" to requestPath,
                            "available_paths" to paths.map { it.path },
                        ),
                    )

                is StaticSiteResolution.Unresolvable ->
                    Output.error(
                        "unresolvable",
                        "no server returned a blob matching the manifest hash",
                        mapOf(
                            "path" to requestPath,
                            "sha256" to resolution.hash,
                            "servers" to servers,
                        ),
                    )

                is StaticSiteResolution.Resolved -> {
                    emitResolved(resolution, requestPath, manifest, identifier, outFile, maxInlineBytes)
                    0
                }
            }
        } finally {
            ctx.close()
        }
    }

    private fun emitResolved(
        resolved: StaticSiteResolution.Resolved,
        requestPath: String,
        manifest: SiteManifest,
        identifier: String?,
        outFile: String?,
        maxInlineBytes: Long,
    ) {
        val base =
            linkedMapOf<String, Any?>(
                "found" to true,
                "verified" to true,
                "request_path" to requestPath,
                "manifest_path" to resolved.path,
                "sha256" to resolved.hash,
                "content_type" to resolved.contentType,
                "size" to resolved.bytes.size,
                "server" to resolved.server,
                "manifest_event_id" to manifest.id,
                "d" to identifier,
            )

        if (outFile != null) {
            File(outFile).writeBytes(resolved.bytes)
            base["out"] = outFile
        } else if (isTextual(resolved.contentType) && resolved.bytes.size <= maxInlineBytes) {
            base["content"] = resolved.bytes.decodeToString()
        } else {
            base["note"] = "binary or large blob not inlined; pass --out FILE to save it"
        }

        Output.emit(base)
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
                    SiteManifest(event.id, event.createdAt, event.paths(), event.servers())
                } else {
                    null
                }
            is RootSiteEvent ->
                if (identifier == null) {
                    SiteManifest(event.id, event.createdAt, event.paths(), event.servers())
                } else {
                    null
                }
            else -> null
        }

    private fun commaList(value: String?): List<String> =
        value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun isTextual(contentType: String): Boolean =
        contentType.startsWith("text/") ||
            contentType.startsWith("application/json") ||
            contentType.startsWith("application/xml") ||
            contentType.startsWith("image/svg")

    /**
     * Flattened view of either manifest event type (`RootSiteEvent` /
     * `NamedSiteEvent`) so the rest of the command doesn't branch on Root vs Named.
     */
    private class SiteManifest(
        val id: String,
        val createdAt: Long,
        val paths: List<PathTag>,
        val servers: List<String>,
    )
}
