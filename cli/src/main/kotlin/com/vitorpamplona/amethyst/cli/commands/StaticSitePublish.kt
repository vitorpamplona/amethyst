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
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.tags.IconTag
import com.vitorpamplona.quartz.nip5dNapplets.tags.ServerOrigin
import com.vitorpamplona.quartz.nip5dNapplets.tags.SourceTag
import com.vitorpamplona.quartz.utils.EventFactory
import java.io.File

/**
 * Shared "ship a directory as a NIP-5A/5D manifest" flow for `amy nsite publish` and
 * `amy napplet publish`. Uploads the tree to Blossom (commons [StaticSitePublisher]), then lets the
 * caller turn the resulting `path → sha256` tags into the kind-specific manifest event ([buildEvent]),
 * which this signs with the account key and broadcasts. Thin assembly: the upload lives in commons,
 * the event shape in quartz, and the publish plumbing in [Context].
 */
object StaticSitePublish {
    /** A bare NAP domain name (`relay`, `theme`), as NIP-5D's R and O tags carry it. */
    private val NAP_DOMAIN = Regex("[a-z][a-z0-9-]*")

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
        val optional: List<String> = emptyList(),
        val iconMime: String? = null,
        val archetypes: List<String> = emptyList(),
        val intents: List<List<String>> = emptyList(),
    )

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
        usage: String,
        napplet: Boolean = false,
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
        val optional = if (napplet) StaticSiteFetch.commaList(args.flag("optional")) else emptyList()
        val iconMime = if (napplet) args.flag("icon-mime") else null
        val archetypes = if (napplet) StaticSiteFetch.commaList(args.flag("archetypes")) else emptyList()
        val intents = if (napplet) StaticSiteFetch.commaList(args.flag("intents")).map { it.split(' ').filter(String::isNotBlank) } else emptyList()
        val extraRelays = StaticSiteFetch.commaList(args.flag("relay"))
        args.rejectUnknown()

        if (napplet) {
            val files = if (source.isDirectory) source.walkTopDown().filter { it.isFile }.toList() else listOf(source)
            if (files.size != 1 || files.single().name != "index.html" ||
                (source.isDirectory && files.single().parentFile.canonicalFile != source.canonicalFile)
            ) {
                return Output.error("bad_args", "NIP-5D requires a single self-contained index.html; bundle assets inline before publishing")
            }
            if (description.isNullOrBlank()) return Output.error("bad_args", "NIP-5D requires a non-empty --description")
            if (icon != null && IconTag.parse(arrayOf(IconTag.TAG_NAME, icon, iconMime.orEmpty())) == null) {
                return Output.error("bad_args", "--icon takes the icon blob's lowercase sha256 with --icon-mime image/png|image/jpeg|image/webp")
            }
            if (sourceUrl != null && !SourceTag.isLocation(sourceUrl)) {
                return Output.error("bad_args", "--source must be an http(s), ssh or git URL, or user@host:path")
            }
            if (intents.any { it.isEmpty() }) return Output.error("bad_args", "each --intents entry names an intent, then its parameters")
            (requires + optional).firstOrNull { !NAP_DOMAIN.matches(it) }?.let {
                return Output.error("bad_args", "--requires/--optional take bare lowercase NAP domains (relay, theme), not '$it'")
            }
            if (identifier != null && identifier.isBlank()) return Output.error("bad_args", "--d needs a non-empty identifier; omit it for a root napplet")
            servers.firstOrNull { !ServerOrigin.isValid(it) }?.let {
                return Output.error("bad_args", "--server takes Blossom origins (https://host[:port]), not '$it'")
            }
            // Dry-build the manifest with a placeholder artifact and refuse what quartz would call
            // invalid, before uploading anything: amy must never publish a napplet it then rejects.
            val draft = buildEvent(Manifest(identifier, listOf(PathTag(NappletManifest.INDEX_PATH, "00".repeat(32))), servers, requires, title, description, sourceUrl, icon, optional, iconMime, archetypes, intents))
            val draftEvent = EventFactory.create<Event>("00".repeat(32), "00".repeat(32), draft.createdAt, draft.kind, draft.tags, draft.content, "")
            if ((draftEvent as? NappletManifest)?.isValid() != true) return Output.error("bad_args", "these flags do not make a valid NIP-5D manifest")
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val result =
                try {
                    StaticSitePublisher().uploadTree(source, servers.first(), ctx.signer)
                } catch (e: Exception) {
                    return Output.error("upload_failed", e.message ?: "upload failed")
                }

            val manifest = Manifest(identifier, result.pathTags, servers, requires, title, description, sourceUrl, icon, optional, iconMime, archetypes, intents)
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
                    (if (napplet) "artifact_sha256" else "aggregate_sha256") to (if (napplet) result.pathTags.single().hash else SiteAggregateHash.compute(result.pathTags)),
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
