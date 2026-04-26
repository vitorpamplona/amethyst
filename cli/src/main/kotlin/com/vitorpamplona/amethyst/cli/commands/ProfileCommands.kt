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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * `amy profile <show|edit>` — read and update the current user's
 * NIP-01 kind:0 metadata event.
 *
 * `show` accepts an optional user identifier (npub/nprofile/64-hex/NIP-05); when
 * omitted, it reports the current account's profile. `edit` only mutates the
 * caller's own profile and patches whichever fields are supplied — unset flags
 * leave the existing values untouched, blank values delete the field (mirroring
 * [MetadataEvent.updateFromPast]).
 */
object ProfileCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "profile <show|edit> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "show" -> show(dataDir, rest)
            "edit" -> edit(dataDir, rest)
            else -> Output.error("bad_args", "profile ${tail[0]}")
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val refresh = args.bool("refresh")
        val timeoutSecs = args.longFlag("timeout", 8L)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val pubKey =
                args.positionalOrNull(0)?.let { ctx.requireUserHex(it) }
                    ?: ctx.identity.pubKeyHex
            val isSelf = pubKey == ctx.identity.pubKeyHex

            // Cache-first: serve from the local store unless --refresh is
            // set. The store is the source of truth (see cli/README.md);
            // every event drained from a relay has already been persisted
            // here, so an offline read just works after the first sync.
            val cached = if (!refresh) ctx.profileOf(pubKey) else null
            val event: MetadataEvent?
            val source: String
            val queried: List<NormalizedRelayUrl>
            if (cached != null) {
                event = cached
                source = "cache"
                queried = emptyList()
            } else {
                val relays = relaysForReadingProfile(ctx, isSelf)
                event = fetchLatestMetadata(ctx, pubKey, relays, timeoutSecs * 1000)
                source = "relays"
                queried = relays.toList()
            }

            if (event == null) {
                Output.emit(
                    mapOf(
                        "pubkey" to pubKey,
                        "found" to false,
                        "source" to source,
                        "queried_relays" to queried.map { it.url },
                    ),
                )
                return 0
            }
            val metadata =
                try {
                    Output.mapper.readTree(event.content)
                } catch (_: Exception) {
                    null
                }
            Output.emit(
                mapOf(
                    "pubkey" to pubKey,
                    "found" to true,
                    "source" to source,
                    "event_id" to event.id,
                    "created_at" to event.createdAt,
                    "metadata" to (metadata ?: emptyMap<String, Any?>()),
                    "queried_relays" to queried.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.flag("name")
        val displayName = args.flag("display-name")
        val about = args.flag("about")
        val picture = args.flag("picture")
        val banner = args.flag("banner")
        val website = args.flag("website")
        val nip05 = args.flag("nip05")
        val lud16 = args.flag("lud16")
        val lud06 = args.flag("lud06")
        val pronouns = args.flag("pronouns")
        val twitter = args.flag("twitter")
        val mastodon = args.flag("mastodon")
        val github = args.flag("github")
        val timeoutSecs = args.longFlag("timeout", 8L)

        val touched =
            listOf(name, displayName, about, picture, banner, website, nip05, lud16, lud06, pronouns, twitter, mastodon, github)
                .any { it != null }
        if (!touched) {
            return Output.error(
                "bad_args",
                "profile edit needs at least one of " +
                    "--name --display-name --about --picture --banner --website " +
                    "--nip05 --lud16 --lud06 --pronouns --twitter --mastodon --github",
            )
        }

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val targets = ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }
            val latest =
                fetchLatestMetadata(
                    ctx,
                    ctx.identity.pubKeyHex,
                    targets,
                    timeoutSecs * 1000,
                )

            val template =
                if (latest != null) {
                    MetadataEvent.updateFromPast(
                        latest = latest,
                        name = name,
                        displayName = displayName,
                        picture = picture,
                        banner = banner,
                        website = website,
                        about = about,
                        nip05 = nip05,
                        lnAddress = lud16,
                        lnURL = lud06,
                        pronouns = pronouns,
                        twitter = twitter,
                        mastodon = mastodon,
                        github = github,
                    )
                } else {
                    MetadataEvent.createNew(
                        name = name,
                        displayName = displayName,
                        picture = picture,
                        banner = banner,
                        website = website,
                        about = about,
                        nip05 = nip05,
                        lnAddress = lud16,
                        lnURL = lud06,
                        pronouns = pronouns,
                        twitter = twitter,
                        mastodon = mastodon,
                        github = github,
                    )
                }

            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, targets)

            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "created_at" to signed.createdAt,
                    "based_on" to latest?.id,
                    "metadata" to Output.mapper.readTree(signed.content),
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * For our own profile, our outbox relays are authoritative; for someone
     * else's profile, fall back to the bootstrap union so we still find a
     * kind:0 even when our relay set and theirs are disjoint.
     */
    private suspend fun relaysForReadingProfile(
        ctx: Context,
        isSelf: Boolean,
    ): Set<NormalizedRelayUrl> =
        if (isSelf) {
            ctx.outboxRelays().ifEmpty { ctx.bootstrapRelays() }
        } else {
            ctx.bootstrapRelays()
        }

    private suspend fun fetchLatestMetadata(
        ctx: Context,
        pubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): MetadataEvent? {
        if (relays.isEmpty()) return null
        val filter = Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubKey), limit = 1)
        val filterMap = relays.associateWith { listOf(filter) }
        val received = ctx.drain(filterMap, timeoutMs)
        return received
            .mapNotNull { (_, ev) -> ev as? MetadataEvent }
            .filter { it.pubKey == pubKey }
            .maxByOrNull { it.createdAt }
    }
}
