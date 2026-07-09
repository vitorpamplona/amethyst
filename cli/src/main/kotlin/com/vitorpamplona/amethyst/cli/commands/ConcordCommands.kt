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
import com.vitorpamplona.amethyst.cli.stores.ConcordStore
import com.vitorpamplona.amethyst.cli.stores.StoredCommunity
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy concord …` — create, join, and drive Concord Channels (encrypted,
 * serverless communities). Thin assembly over [ConcordActions] (commons) and
 * [Context]; secrets persist in `~/.amy/<account>/concord.json`.
 */
object ConcordCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "concord",
            tail,
            "concord <create|list|channels|send|read|invite|join>",
            mapOf(
                "create" to { rest -> create(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
                "channels" to { rest -> ConcordChannelCommands.channels(dataDir, rest) },
                "send" to { rest -> ConcordChannelCommands.send(dataDir, rest) },
                "read" to { rest -> ConcordChannelCommands.read(dataDir, rest) },
                "invite" to { rest -> invite(dataDir, rest) },
                "join" to { rest -> join(dataDir, rest) },
            ),
        )

    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.requireFlag("name")
        val about = args.flag("about")
        val relayArg = parseRelays(args.flag("relays"))

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = relayArg.ifEmpty { ctx.outboxRelays().map { it.url } }
            val community = ConcordActions.createCommunity(ctx.signer, name, TimeUtils.now(), about, relays)

            val publishTo = normalize(relays).ifEmpty { ctx.outboxRelays() }
            val acked = mutableSetOf<NormalizedRelayUrl>()
            for (wrap in community.genesisWraps) acked += ctx.publish(wrap, publishTo).filterValues { it }.keys

            ConcordStore(dataDir.concordFile).upsert(
                StoredCommunity(
                    name = name,
                    communityId = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    generalChannelId = community.generalChannelIdHex,
                    relays = relays,
                ),
            )

            Output.emit(
                mapOf(
                    "community_id" to community.communityIdHex,
                    "name" to name,
                    "general_channel_id" to community.generalChannelIdHex,
                    "published_to" to acked.map { it.url },
                ),
            )
            return 0
        }
    }

    private fun list(
        dataDir: DataDir,
        @Suppress("UNUSED_PARAMETER") rest: Array<String>,
    ): Int {
        val communities =
            ConcordStore(dataDir.concordFile).load().map {
                mapOf("name" to it.name, "community_id" to it.communityId, "owner" to it.owner, "relays" to it.relays)
            }
        Output.emit(mapOf("communities" to communities))
        return 0
    }

    private suspend fun invite(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val base = args.flag("base", "https://vector.chat")!!

        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return notFound(handle)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val invite = ConcordActions.inviteFor(sc.communityId, sc.owner, sc.ownerSalt, sc.root, sc.rootEpoch, sc.name, sc.relays)
            val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), sc.relays)
            val acked = ctx.publish(minted.bundleEvent, relaysFor(ctx, sc)).filterValues { it }.keys

            Output.emit(
                mapOf(
                    "url" to minted.url,
                    "bundle_event_id" to minted.bundleEvent.id,
                    "link_signer" to minted.linkSignerPubKey,
                    "published_to" to acked.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun join(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val url = args.positional(0, "url")
        val parsed = ConcordActions.parseInviteLink(url) ?: return Output.error("bad_args", "not a valid invite link").let { 2 }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = (normalize(parsed.fragment.relays) + ctx.bootstrapRelays())
            val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }).map { it.second }
            val bundle =
                wraps.firstNotNullOfOrNull { ConcordActions.openBundle(it, parsed.fragment.token) }
                    ?: return Output.error("not_found", "no valid bundle for this link").let { 1 }

            ConcordStore(dataDir.concordFile).upsert(
                StoredCommunity(
                    name = bundle.name,
                    communityId = bundle.communityId,
                    owner = bundle.owner,
                    ownerSalt = bundle.ownerSalt,
                    root = bundle.communityRoot,
                    rootEpoch = bundle.rootEpoch,
                    relays = bundle.relays,
                ),
            )
            Output.emit(mapOf("community_id" to bundle.communityId, "name" to bundle.name, "relays" to bundle.relays))
            return 0
        }
    }

    // ---- shared helpers (used by ConcordChannelCommands too) ------------------

    fun parseRelays(csv: String?): List<String> = csv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun normalize(urls: List<String>): Set<NormalizedRelayUrl> = urls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    suspend fun relaysFor(
        ctx: Context,
        sc: StoredCommunity,
    ): Set<NormalizedRelayUrl> = normalize(sc.relays).ifEmpty { ctx.outboxRelays() }

    fun notFound(handle: String): Int {
        Output.error("not_found", "no joined community matching '$handle' — run `amy concord list`")
        return 1
    }
}
