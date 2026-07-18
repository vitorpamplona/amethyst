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
import com.vitorpamplona.amethyst.cli.stores.StoredHeldRoot
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy concord …` — create, join, and drive Concord Channels (encrypted,
 * serverless communities). Thin assembly over [ConcordActions] (commons) and
 * [Context]; secrets persist in `~/.amy/<account>/concord.json`.
 */
object ConcordCommands {
    val USAGE: String =
        """
        |Concord Channels (encrypted, serverless communities):
        |  concord create --name NAME [--about T]      create an encrypted Concord community
        |          [--relay wss://a,wss://b]            (--relays is accepted as an alias)
        |  concord list                                list joined Concord communities
        |  concord import                              fetch + decrypt this account's kind:13302
        |                                               community list (carries heldRoots, CORD-06)
        |  concord channels COMMUNITY                  list a community's channels
        |  concord send COMMUNITY CHANNEL TEXT         post a message (CHANNEL = general|name|id)
        |  concord read COMMUNITY CHANNEL [--limit N]  read a channel's messages (default 50);
        |          [--epoch N] [--root HEX]             --epoch/--root read a prior epoch's plane
        |  concord invite COMMUNITY [--base URL]       mint + publish a shareable invite link
        |  concord join URL                            redeem an invite link and save the community
        |  concord roles COMMUNITY                     list live roles + current banlist (CORD-04)
        |  concord role COMMUNITY NAME POSITION PERM…  define a role (perms by name, e.g. BAN KICK)
        |  concord grant COMMUNITY USER ROLE-ID        grant a role to a member
        |  concord ban COMMUNITY USER                  ban a member
        |  concord unban COMMUNITY USER                unban a member
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "concord",
            tail,
            "concord <create|list|import|channels|send|read|invite|join|roles|role|grant|ban|unban>",
            help = USAGE,
            routes =
                mapOf(
                    "create" to { rest -> create(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                    "import" to { rest -> import(dataDir, rest) },
                    "channels" to { rest -> ConcordChannelCommands.channels(dataDir, rest) },
                    "send" to { rest -> ConcordChannelCommands.send(dataDir, rest) },
                    "read" to { rest -> ConcordChannelCommands.read(dataDir, rest) },
                    "invite" to { rest -> invite(dataDir, rest) },
                    "join" to { rest -> join(dataDir, rest) },
                    "roles" to { rest -> ConcordModCommands.roles(dataDir, rest) },
                    "role" to { rest -> ConcordModCommands.defineRole(dataDir, rest) },
                    "grant" to { rest -> ConcordModCommands.grant(dataDir, rest) },
                    "ban" to { rest -> ConcordModCommands.ban(dataDir, rest) },
                    "unban" to { rest -> ConcordModCommands.unban(dataDir, rest) },
                ),
        )

    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.requireFlag("name")
        val about = args.flag("about")
        // `--relay` is the canonical spelling; `--relays` stays as a silent alias.
        val relayArg = parseRelays(args.flag("relay") ?: args.flag("relays"))
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = relayArg.ifEmpty { ctx.outboxRelays().map { it.url } }
            val community = ConcordActions.createCommunity(ctx.signer, name, TimeUtils.now(), about, relays)

            val publishTo = normalize(relays).ifEmpty { ctx.outboxRelays() }
            val acked = mutableSetOf<NormalizedRelayUrl>()
            for (wrap in community.genesisWraps) acked += ctx.publish(wrap, publishTo).filterValues { it.accepted }.keys

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

    /**
     * Fetch this account's own encrypted kind-13302 Concord community list, decrypt it, and
     * upsert every community into the local store — crucially carrying each community's
     * `heldRoots` (the prior-epoch access roots Amethyst accumulates across Refoundings, CORD-06).
     * With those persisted, `amy concord read --epoch <n>` can re-derive a pre-refounding Chat
     * Plane. A fresh account (never lived through a Refounding) simply has empty `heldRoots`.
     */
    private suspend fun import(
        dataDir: DataDir,
        @Suppress("UNUSED_PARAMETER") rest: Array<String>,
    ): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = (ctx.outboxRelays() + ctx.bootstrapRelays())
            val filter = Filter(kinds = listOf(ConcordCommunityListEvent.KIND), authors = listOf(ctx.signer.pubKey))
            val events = ctx.drain(relays.associateWith { listOf(filter) }).map { it.second }
            val newest =
                events.filterIsInstance<ConcordCommunityListEvent>().maxByOrNull { it.createdAt }
                    ?: return Output.error("not_found", "no kind-13302 Concord list published by this account").let { 1 }

            val entries =
                try {
                    newest.decrypt(ctx.signer)
                } catch (e: Exception) {
                    return Output.error("decrypt_failed", "could not decrypt kind-13302: ${e.message}").let { 1 }
                }
            val store = ConcordStore(dataDir.concordFile)
            val existing = store.load().associateBy { it.communityId }
            val imported =
                entries.map { e ->
                    val prior = existing[e.id]
                    store.upsert(
                        StoredCommunity(
                            name = e.name.ifBlank { prior?.name ?: "" },
                            communityId = e.id,
                            owner = e.owner,
                            ownerSalt = e.ownerSalt,
                            root = e.root,
                            rootEpoch = e.rootEpoch,
                            generalChannelId = prior?.generalChannelId ?: "",
                            relays = e.relays,
                            heldRoots = e.heldRoots.map { StoredHeldRoot(it.epoch, it.key) },
                        ),
                    )
                    mapOf(
                        "name" to e.name,
                        "community_id" to e.id,
                        "root_epoch" to e.rootEpoch,
                        "held_roots" to e.heldRoots.map { mapOf("epoch" to it.epoch, "root" to it.key) },
                    )
                }
            Output.emit(mapOf("imported" to imported))
            return 0
        }
    }

    private suspend fun invite(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val base = args.flag("base", "https://vector.chat")!!
        args.rejectUnknown()

        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return notFound(handle)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val invite = ConcordActions.inviteFor(sc.communityId, sc.owner, sc.ownerSalt, sc.root, sc.rootEpoch, sc.name, sc.relays)
            val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), sc.relays)
            val ack = ctx.publish(minted.bundleEvent, relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, minted.bundleEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "url" to minted.url,
                    "bundle_event_id" to minted.bundleEvent.id,
                    "link_signer" to minted.linkSignerPubKey,
                ) + RawEventSupport.ackFields(ack),
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
        args.rejectUnknown()
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
