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
import com.vitorpamplona.amethyst.commons.service.upload.StaticSitePublisher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteAggregateHash
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import java.io.File

/**
 * Shared "ship a directory as a NIP-5A/5D manifest" flow for `amy nsite publish` and
 * `amy napplet publish`. Uploads the tree to Blossom (commons [StaticSitePublisher]), then lets the
 * caller turn the resulting `path → sha256` tags into the kind-specific manifest event ([buildEvent]),
 * which this signs with the account key and broadcasts. Thin assembly: the upload lives in commons,
 * the event shape in quartz, and the publish plumbing in [Context].
 */
object StaticSitePublish {
    /** Pieces resolved from the CLI args, handed to the kind-specific event builder. */
    class Manifest(
        val identifier: String?,
        val paths: List<PathTag>,
        val servers: List<String>,
        val requires: List<String>,
        val title: String?,
        val description: String?,
        val source: String?,
        val icon: String?,
    )

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
        usage: String,
        buildEvent: (Manifest) -> EventTemplate<out Event>,
    ): Int {
        val args = Args(rest)
        val dirArg = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val source = File(dirArg)
        if (!source.exists()) return Output.error("bad_args", "no such file or directory: $dirArg")

        val servers = StaticSiteFetch.commaList(args.flag("server"))
        if (servers.isEmpty()) return Output.error("bad_args", "publish requires --server <blossom-url> (comma-separated for mirrors)")

        val identifierAlias = args.flag("identifier")
        val identifier = args.flag("d") ?: identifierAlias
        val requires = StaticSiteFetch.commaList(args.flag("requires"))
        val title = args.flag("title")
        val description = args.flag("description")
        val sourceUrl = args.flag("source")
        val icon = args.flag("icon")
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val result =
                try {
                    StaticSitePublisher().uploadTree(source, servers.first(), ctx.signer)
                } catch (e: Exception) {
                    return Output.error("upload_failed", e.message ?: "upload failed")
                }

            val manifest = Manifest(identifier, result.pathTags, servers, requires, title, description, sourceUrl, icon)
            val signed = ctx.signer.sign(buildEvent(manifest))

            val relays =
                extraRelays
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()
                    .ifEmpty { ctx.outboxRelays() }
                    .ifEmpty { ctx.bootstrapRelays() }

            val ack = ctx.publish(signed, relays)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }

            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "d" to identifier,
                    "title" to title,
                    "icon" to icon,
                    "servers" to servers,
                    "requires" to requires,
                    "aggregate_sha256" to SiteAggregateHash.compute(result.pathTags),
                    "files" to
                        result.uploaded.map {
                            mapOf("path" to it.path, "sha256" to it.sha256, "size_bytes" to it.size, "url" to it.url)
                        },
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
