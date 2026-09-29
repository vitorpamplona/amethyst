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
import com.vitorpamplona.amethyst.cli.stores.StoredPrivateChannel
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordReceive
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.concord.cord02Community.ConcordListFragmentSet
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordLimits
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteList
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListDocument
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEntry
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEvent
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListTombstone
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
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
        |  concord import                              fetch + decrypt this account's kind:33302
        |                                               community list (carries heldRoots, CORD-06)
        |  concord channels COMMUNITY                  list a community's channels
        |  concord send COMMUNITY CHANNEL TEXT         post a message (CHANNEL = general|name|id)
        |  concord read COMMUNITY CHANNEL [--limit N]  read a channel's messages (default 50);
        |          [--epoch N] [--root HEX]             --epoch/--root read a prior epoch's plane
        |  concord invite COMMUNITY [--base URL]       mint + publish a shareable invite link
        |  concord revoke COMMUNITY TOKEN|URL          retire a link you minted: publishes a vsk=9
        |                                               tombstone at its coordinate, then tombstones
        |                                               it in your invite list so it stays retired
        |  concord join URL                            redeem an invite link and save the community
        |  concord rekey [COMMUNITY]                   follow a Refounding we were re-keyed for:
        |                                               open our blob and adopt the new epoch
        |  concord recover [COMMUNITY] [--rejoin]      re-resolve the joined-through invite link and
        |                                               report whether a Refounding left us behind;
        |                                               --rejoin re-accepts that link (a bundle never
        |                                               moves the base on its own, CORD-06 §2);
        |                                               refuses if that epoch banned us
        |  concord roles COMMUNITY                     list live roles + current banlist (CORD-04),
        |                                               and public: true/false + live invite links
        |                                               from the folded registries (CORD-05 §5)
        |  concord role COMMUNITY NAME POSITION PERM…  define a role (perms by name, e.g. BAN KICK)
        |  concord grant COMMUNITY USER ROLE-ID        grant a role to a member
        |  concord ban COMMUNITY USER                  ban a member
        |  concord pins COMMUNITY CHANNEL              the channel's verified Pin List (CORD-04 §7)
        |  concord pin COMMUNITY CHANNEL RUMOR_ID      pin a message (PIN_MESSAGES); proves it with
        |                                               its original seal, capped at 25 / 32 KiB
        |  concord unpin COMMUNITY CHANNEL RUMOR_ID    unpin a message (the next edition without it)
        |  concord unban COMMUNITY USER                unban a member
        |  concord refound COMMUNITY --remove U[,U]    CORD-06 Refounding: rotate the root (and the
        |                                               control_root) so removed members lose every
        |                                               key — the hard removal a ban cannot give
        |  concord refound COMMUNITY --privatize       a Refounding that removes nobody: converts a
        |                                               Public community to Private (owed after the
        |                                               last live invite link is revoked, CORD-05 §2)
        |  concord dissolve COMMUNITY --yes            CORD-02 §9: owner-only, IRREVERSIBLE tombstone
        |                                               that seals the community read-only for everyone
        |  concord timer COMMUNITY [off|SECONDS|1d|1w|30d|90d|1y]
        |                                               CORD-08 disappearing messages: print the timer,
        |                                               or set it (MANAGE_METADATA) + post channel notices
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "concord",
            tail,
            "concord <create|list|import|channels|send|read|invite|revoke|join|recover|rekey|roles|role|grant|ban|unban|pins|pin|unpin|refound|dissolve|timer>",
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
                    "revoke" to { rest -> revoke(dataDir, rest) },
                    "join" to { rest -> join(dataDir, rest) },
                    "recover" to { rest -> recover(dataDir, rest) },
                    "rekey" to { rest -> rekey(dataDir, rest) },
                    "roles" to { rest -> ConcordModCommands.roles(dataDir, rest) },
                    "role" to { rest -> ConcordModCommands.defineRole(dataDir, rest) },
                    "grant" to { rest -> ConcordModCommands.grant(dataDir, rest) },
                    "ban" to { rest -> ConcordModCommands.ban(dataDir, rest) },
                    "unban" to { rest -> ConcordModCommands.unban(dataDir, rest) },
                    "pins" to { rest -> ConcordPinCommands.pins(dataDir, rest) },
                    "pin" to { rest -> ConcordPinCommands.pin(dataDir, rest) },
                    "unpin" to { rest -> ConcordPinCommands.unpin(dataDir, rest) },
                    "refound" to { rest -> ConcordModCommands.refound(dataDir, rest) },
                    "dissolve" to { rest -> ConcordModCommands.dissolve(dataDir, rest) },
                    "timer" to { rest -> ConcordModCommands.timer(dataDir, rest) },
                ),
        )

    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.requireFlag("name")
        val about = args.flag("about")
        // `--relay` is the canonical spelling; `--relays` stays as a silent alias —
        // read eagerly so passing both spellings doesn't trip rejectUnknown().
        val relaysAlias = args.flag("relays")
        val relayArg = parseRelays(args.flag("relay") ?: relaysAlias)
        args.rejectUnknown()
        // CORD-02 §6 caps, which every reader also enforces at fold.
        if (!ConcordLimits.nameFits(name)) return Output.error("bad_args", "community name exceeds ${ConcordLimits.NAME_MAX_BYTES} bytes").let { 2 }
        if (!ConcordLimits.descriptionFits(about)) return Output.error("bad_args", "description exceeds ${ConcordLimits.DESCRIPTION_MAX_BYTES} bytes").let { 2 }

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
                    // The creator is the founding staff member: it keeps the write secret and
                    // publishes the pubkey to everyone else (CORD-02 §2).
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
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
     * Fetch this account's own encrypted Concord Community List (the kind-33302 fragments, plus the
     * retired kind-13302 event as a rescue source), decrypt it, and
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
            // The fragmented List (33302, CORD-02 §8) plus the retired single event (13302), which is
            // still read as a rescue source for memberships only it carries.
            val filter = Filter(kinds = listOf(ConcordCommunityListFragmentEvent.KIND, ConcordCommunityListEvent.KIND), authors = listOf(ctx.signer.pubKey))
            val events = ctx.drain(relays.associateWith { listOf(filter) }).map { it.second }
            val set = ConcordListFragmentSet.resolve(events.filterIsInstance<ConcordCommunityListFragmentEvent>(), ctx.signer)
            val legacy = events.filterIsInstance<ConcordCommunityListEvent>().maxByOrNull { it.createdAt }
            if (set.isEmpty && legacy == null) {
                return Output.error("not_found", "no Concord Community List (kind 33302 or 13302) published by this account").let { 1 }
            }
            val legacyPlaintext = legacy?.decryptPlaintext(ctx.signer)
            if (set.held.isEmpty() && legacyPlaintext == null) {
                return Output.error("decrypt_failed", "could not decrypt this account's Concord Community List").let { 1 }
            }
            val entries = ConcordCommunityList.decodeDocument(ConcordCommunityList.readWithLegacy(set, legacyPlaintext)).entries
            val store = ConcordStore(dataDir.concordFile)
            val existing = store.load().associateBy { it.communityId }
            val imported =
                entries.map { e ->
                    val prior = existing[e.id]
                    // Control key material is per-epoch (CORD-02 §2): a stored value may only
                    // backstop a list entry from the SAME epoch (e.g. another client republished
                    // the list without the extension fields). Across a rotation the old pair is
                    // stale — a prior-epoch control_root would derive a wrong address entirely,
                    // and a prior-epoch control_pk would shadow a legacy rotation's address — so
                    // it must never be carried forward (the invariant adoption enforces with its
                    // derive-check, which this path has no way to run).
                    val priorSameEpoch = prior?.takeIf { it.rootEpoch == e.rootEpoch }
                    store.upsert(
                        StoredCommunity(
                            name = e.name.ifBlank { prior?.name ?: "" },
                            communityId = e.id,
                            owner = e.owner,
                            ownerSalt = e.ownerSalt,
                            root = e.root,
                            rootEpoch = e.rootEpoch,
                            // Carried straight from the list entry: the Control Plane address is
                            // delivered, never derivable (CORD-02 §2), and the write secret only
                            // rides the list when this account is staff. Both blank on a legacy
                            // community, which keeps its old single-key plane.
                            controlPk = e.controlPk ?: priorSameEpoch?.controlPk ?: "",
                            controlRoot = e.controlRoot ?: priorSameEpoch?.controlRoot ?: "",
                            generalChannelId = prior?.generalChannelId ?: "",
                            relays = e.relays,
                            heldRoots = e.heldRoots.map { StoredHeldRoot(it.epoch, it.key, it.controlPk ?: "", it.controlRoot ?: "") },
                            // Survives every merge: losing the anchor makes the NEXT exclusion
                            // unrecoverable, so a list entry without one must not clear ours.
                            inviteRef = e.inviteRef ?: prior?.inviteRef ?: "",
                            privateChannels = e.privateChannels.filter { it.key.isNotBlank() }.map { StoredPrivateChannel(it.channelId, it.key, it.epoch, it.name) },
                        ),
                    )
                    mapOf(
                        "name" to e.name,
                        "community_id" to e.id,
                        "root_epoch" to e.rootEpoch,
                        "control_pk" to (e.controlPk ?: ""),
                        "staff" to (e.controlRoot != null),
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
            // The joiner cannot derive the Control Plane address, so the invite carries it
            // (CORD-05 §1); omitted for a legacy community, which has none to carry.
            // The creator rides in the bundle so joiners echo it in their Guestbook Join (CORD-05 §1).
            val invite = ConcordActions.inviteFor(sc.communityId, sc.owner, sc.ownerSalt, sc.root, sc.rootEpoch, sc.name, sc.relays, sc.controlPk.ifBlank { null }, creator = ctx.signer.pubKey)
            // At most 3 bootstrap relays ride in the fragment (CORD-05 §3); the codec truncates.
            val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), sc.relays)
            // Record the link BEFORE publishing the bundle (CORD-05, kind 13303): a link whose
            // `signer_sk` was never stored can never be refreshed, so the next Refounding orphans
            // it and every holder is stranded. Better to mint nothing than to hand out a link that
            // is already doomed.
            val recorded =
                publishInviteList(
                    ctx,
                    ConcordInviteListDocument(
                        entries =
                            listOf(
                                ConcordInviteListEntry(
                                    token = minted.token.toHexKey(),
                                    signerSk = minted.linkSignerPrivKey.toHexKey(),
                                    communityId = sc.communityId,
                                    url = minted.url,
                                    createdAt = TimeUtils.now(),
                                ),
                            ),
                    ),
                )
            if (recorded == null) {
                return Output.error(
                    "invite_unrecordable",
                    "could not record the link signer in your invite list (kind 13303), so this link could never be refreshed after a Refounding — not minting it",
                )
            }

            val ack = ctx.publish(minted.bundleEvent, relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, minted.bundleEvent.id)?.let { return it }

            // "A Registry edit accompanies every mint" (CORD-05 §5): the link now makes the community Public.
            val registry = ConcordModCommands.publishInviteRegistry(ctx, sc, dataDir, recorded, minted = listOf(minted.linkSignerPubKey))

            Output.emit(
                mapOf(
                    "url" to minted.url,
                    "bundle_event_id" to minted.bundleEvent.id,
                    "link_signer" to minted.linkSignerPubKey,
                ) + registry + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /**
     * `amy concord revoke <community> <token|url>` — retires one link this account minted.
     *
     * Two records have to agree for a link to be gone, and they fail differently, so the order is
     * deliberate. The wire tombstone (`vsk=9` at the link's own coordinate) is what actually stops
     * a join, and publishing it needs the `signer_sk` that only the kind-13303 Invite List holds.
     * The list tombstone is bookkeeping: it stops a later Refounding from re-minting the link.
     *
     * So the wire goes first and the list second. The reverse order would delete the entry — a
     * merge drops a tombstoned token's entry terminally — and if the publish then failed, the link
     * would stay live with its `signer_sk` gone and no way left to retire it. A failed list write
     * is recoverable by comparison: the link is already dead on the wire, and the refresh path
     * re-mints only a coordinate that still resolves Live, so it will not resurrect this one.
     */
    private suspend fun revoke(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val link = args.positional(1, "token|url")
        args.rejectUnknown()

        // Accept either the shareable URL (what a creator actually has to hand) or the bare token.
        val token =
            ConcordActions
                .parseInviteLink(link)
                ?.fragment
                ?.token
                ?.toHexKey() ?: link.lowercase()
        if (!TOKEN_HEX.matches(token)) {
            return Output.error("bad_args", "expected an invite URL or a 32-hex-character link token, got '$link'").let { 2 }
        }

        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return notFound(handle)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val list =
                readInviteList(ctx)
                    ?: return Output.error("invite_list_unreadable", "could not read your invite list (kind 13303), so the link signer needed to revoke is unknown — refusing to guess")

            val entry = list.entries.firstOrNull { it.token == token }
            if (entry == null) {
                return if (list.tombstones.any { it.token == token }) {
                    Output.error("already_revoked", "this link was already revoked; its signer_sk is gone from the list, so there is nothing left to re-publish")
                } else {
                    Output.error("not_found", "no link with token $token in your invite list — only the account that minted a link can revoke it")
                }
            }
            if (entry.communityId != sc.communityId) {
                return Output.error("wrong_community", "that link belongs to community ${entry.communityId}, not '$handle' (${sc.communityId})")
            }

            val tombstone = ConcordActions.revokeBundleAt(entry.signerSk.hexToByteArray(), TimeUtils.now())
            val ack = ctx.publish(tombstone, relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, tombstone.id)?.let { return it }

            val recorded =
                publishInviteList(
                    ctx,
                    ConcordInviteListDocument(tombstones = listOf(ConcordInviteListTombstone(token = token, communityId = sc.communityId))),
                )
            if (recorded == null) {
                System.err.println(
                    "[concord] the link is revoked on the wire but the tombstone could not be recorded in your invite list (kind 13303); re-run this command once your outbox relays are reachable",
                )
            }

            // "...and every retire" (CORD-05 §5). Retiring the last live link flips the community
            // Private, which is a Refounding (CORD-05 §2): reported, and run with `refound --privatize`.
            val signer = entry.signerPubKeyHex().lowercase()
            val registry = ConcordModCommands.publishInviteRegistry(ctx, sc, dataDir, recorded ?: list, retired = listOf(signer))
            if (registry["privatized"] == true) {
                System.err.println("[concord] that was the community's last live invite link, so it is Private now: run `amy concord refound ${sc.communityId} --privatize` to rotate its keys (CORD-06 §3)")
            }

            Output.emit(
                mapOf(
                    "revoked" to true,
                    "token" to token,
                    "community_id" to sc.communityId,
                    "link_signer" to signer,
                    "tombstone_event_id" to tombstone.id,
                    "tombstoned_in_list" to (recorded != null),
                ) + registry + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /** A link token is 16 bytes on the wire, so 32 hex characters once stored in the list. */
    private val TOKEN_HEX = Regex("^[0-9a-f]{32}$")

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
            // Resolve the coordinate per CORD-05 §2 rather than opening whatever happens to decrypt:
            // the newest event wins, so a vsk=9 tombstone retires the link even when a stale but
            // still-openable copy is also present. Opening the first wrap that decrypts would let a
            // relay that kept the old version hand out a link its creator revoked — and it cannot
            // tell the user which of "revoked", "expired" or "gone" they are looking at.
            val bundle =
                when (val status = ConcordActions.classifyInvite(wraps, parsed.linkSignerPubKey, parsed.fragment.token)) {
                    is InviteBundleStatus.Live -> status.invite
                    is InviteBundleStatus.Expired -> return Output.error("expired", "this invite link has expired and can no longer be joined")
                    InviteBundleStatus.Revoked -> return Output.error("revoked", "this invite link was revoked by its creator")
                    InviteBundleStatus.Unreadable -> return Output.error("incompatible", "something is published at this link's coordinate, but it is not a bundle this client can open")
                    InviteBundleStatus.Absent -> return Output.error("not_found", "no bundle for this link on any of its relays")
                }

            // Refuse a link that readmits us after we were removed. A Refounding re-mints every
            // outstanding link onto the new root (CORD-05), and an ex-member keeps the URL and its
            // unlock token forever — so without this check the rotation that was supposed to expel
            // them hands them the new keys instead. `recover` has always been ban-gated; `join` is
            // the other door into the same room.
            //
            // Fails CLOSED on an unreadable plane: no verdict, no join. The banlist is only knowable
            // after the bundle yields the root, which is why the check lives here rather than before.
            val joinKeys =
                ConcordActions.controlPlaneKeys(
                    communityRoot = bundle.communityRoot.hexToByteArray(),
                    communityId = bundle.communityId.hexToByteArray(),
                    rootEpoch = bundle.rootEpoch,
                    controlPk = bundle.controlPk,
                )
            val joinRelays = normalize(bundle.relays).ifEmpty { relays }
            val joinEditions =
                ConcordActions.controlEditions(
                    ctx.drain(joinRelays.associateWith { listOf(ConcordActions.planeFilter(joinKeys.address)) }, pendingOnAuthRequired = true).map { it.second },
                    joinKeys,
                )
            if (joinEditions.isEmpty()) {
                return Output.error("control_plane_unreadable", "could not fold this community's Control Plane, so whether it has banned you is unknown — refusing to join")
            }
            if (AuthorityResolver.resolve(joinEditions, bundle.communityId.hexToByteArray(), bundle.owner).isBanned(ctx.signer.pubKey)) {
                return Output.error("banned", "this community has banned this account; the link works but the roster does not admit you (CORD-04)")
            }

            val stored =
                StoredCommunity(
                    name = bundle.name,
                    communityId = bundle.communityId,
                    owner = bundle.owner,
                    ownerSalt = bundle.ownerSalt,
                    root = bundle.communityRoot,
                    rootEpoch = bundle.rootEpoch,
                    // Read access to the Control Plane, never write (CORD-05 §1). Absent = the
                    // community is still pre-split and folds at the legacy address.
                    controlPk = bundle.controlPk ?: "",
                    relays = bundle.relays,
                    // The stranded-recovery anchor: if a later Refounding leaves us out, re-resolving
                    // this link is the only way back (CORD-05/06). Stored bare, domain-agnostic.
                    inviteRef = ConcordActions.bareInviteRef(url) ?: "",
                    privateChannels = ConcordActions.privateChannelKeysOf(bundle).map { StoredPrivateChannel(it.channelId, it.key, it.epoch, it.name) },
                )
            ConcordStore(dataDir.concordFile).upsert(stored)

            // Announce the membership (CORD-05 §6 / CORD-02 §5): a Guestbook Join is how a later
            // Refounding finds this member to re-key, and it echoes the link's attribution so link
            // holders can count per-link joins. Best-effort, like every Guestbook motion.
            val announced = announceGuestbookJoin(ctx, stored, bundle.creatorNpub, bundle.label)
            Output.emit(mapOf("community_id" to bundle.communityId, "name" to bundle.name, "relays" to bundle.relays, "guestbook_join" to announced))
            return 0
        }
    }

    // ---- shared helpers (used by ConcordChannelCommands too) ------------------

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Publishes a Guestbook Join for [sc] at its current epoch, echoing invite attribution; true if a relay took it. */
    suspend fun announceGuestbookJoin(
        ctx: Context,
        sc: StoredCommunity,
        inviteCreator: String?,
        inviteLabel: String?,
    ): Boolean {
        val creator = inviteCreator?.lowercase()?.takeIf { HEX64.matches(it) }
        val label = inviteLabel?.takeIf { creator != null && it.isNotBlank() }
        val guestbook = ConcordActions.guestbookPlane(sc.root.hexToByteArray(), sc.communityId.hexToByteArray(), sc.rootEpoch)
        val wrap = ConcordActions.buildGuestbookJoin(ctx.signer, guestbook, TimeUtils.now(), creator, label)
        val relays = relaysFor(ctx, sc)
        ctx.registerConcordStreamKeys(relays, listOf(guestbook.secretKey))
        return ctx.publish(wrap, relays).values.any { it.accepted }
    }

    /**
     * Whether [sc] carries a valid owner tombstone (CORD-02 §9). Death wins every race: no rekey,
     * recovery or Refounding moves a dissolved community forward.
     */
    suspend fun isDissolved(
        ctx: Context,
        sc: StoredCommunity,
    ): Boolean {
        val grave = ConcordDissolution.planeKey(sc.communityId)
        val relays = relaysFor(ctx, sc)
        ctx.registerConcordStreamKeys(relays, listOf(grave.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(grave.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
        return ConcordDissolution.isDissolved(wraps, sc.communityId, sc.owner)
    }

    fun parseRelays(csv: String?): List<String> = csv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun normalize(urls: List<String>): Set<NormalizedRelayUrl> = urls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    suspend fun relaysFor(
        ctx: Context,
        sc: StoredCommunity,
    ): Set<NormalizedRelayUrl> = normalize(sc.relays).ifEmpty { ctx.outboxRelays() }

    /**
     * The Control Plane keys for [sc] as this account holds them (CORD-02 §5): staff
     * (write key held), member (address held, read-only), or legacy (pre-split, keyed
     * by the `community_root` alone).
     */
    fun controlPlaneKeysFor(sc: StoredCommunity) =
        ConcordActions.controlPlaneKeys(
            communityRoot = sc.root.hexToByteArray(),
            communityId = sc.communityId.hexToByteArray(),
            rootEpoch = sc.rootEpoch,
            controlPk = sc.controlPk.ifBlank { null },
            controlRoot = sc.controlRoot.ifBlank { null },
        )

    /**
     * Adopts the `control_root` a staff-making Grant delivered to us (CORD-04 §3), persisting it to
     * the local store and returning the now-writable keys — or null when nothing was delivered.
     *
     * The decision itself is [ConcordReceive.deliveredControlRoot], shared with Amethyst: it fails
     * closed unless our own fold seats us as staff, the wrap opens under the granter↔member pairwise
     * key, it names this epoch, and the secret derives to exactly the `control_pk` we already hold.
     *
     * Local-only on purpose: Amethyst republishes the kind-13302 list on adoption so a user's other
     * devices follow, and doing that here would need amy to rebuild and sign the whole list. A CLI
     * adoption therefore unblocks *this* account's writes; other devices adopt from their own fold.
     */
    suspend fun adoptDeliveredControlRoot(
        ctx: Context,
        dataDir: DataDir,
        sc: StoredCommunity,
        editions: List<ControlEdition>,
    ): Pair<StoredCommunity, ControlPlaneKeys>? {
        val entry = entryFor(sc)
        val authority = AuthorityResolver.resolve(editions, sc.communityId.hexToByteArray(), sc.owner)
        val delivered = ConcordReceive.deliveredControlRoot(entry, editions, authority, ctx.signer) ?: return null
        val updated = sc.copy(controlRoot = delivered)
        ConcordStore(dataDir.concordFile).upsert(updated)
        return updated to controlPlaneKeysFor(updated)
    }

    /** The quartz list entry a [StoredCommunity] describes — the shape every commons helper takes. */
    fun entryFor(sc: StoredCommunity) =
        ConcordCommunityListEntry(
            id = sc.communityId,
            owner = sc.owner,
            ownerSalt = sc.ownerSalt,
            root = sc.root,
            rootEpoch = sc.rootEpoch,
            controlPk = sc.controlPk.ifBlank { null },
            controlRoot = sc.controlRoot.ifBlank { null },
            heldRoots = sc.heldRoots.map { HeldRoot(it.epoch, it.root, it.controlPk.ifBlank { null }, it.controlRoot.ifBlank { null }) },
            privateChannels = sc.privateChannels.map { PrivateChannelKey(it.channelId, it.key, it.epoch, it.name) },
            relays = sc.relays,
            name = sc.name,
            inviteRef = sc.inviteRef.ifBlank { null },
        )

    /** Folds [entry] back into the stored shape after a rotation is adopted. */
    fun storedFrom(
        sc: StoredCommunity,
        entry: ConcordCommunityListEntry,
    ) = sc.copy(
        root = entry.root,
        rootEpoch = entry.rootEpoch,
        controlPk = entry.controlPk ?: "",
        controlRoot = entry.controlRoot ?: "",
        heldRoots = entry.heldRoots.map { StoredHeldRoot(it.epoch, it.key, it.controlPk ?: "", it.controlRoot ?: "") },
        relays = entry.relays,
        name = entry.name.ifBlank { sc.name },
        inviteRef = entry.inviteRef ?: sc.inviteRef,
        privateChannels = entry.privateChannels.filter { it.key.isNotBlank() }.map { StoredPrivateChannel(it.channelId, it.key, it.epoch, it.name) },
    )

    /**
     * `concord recover [COMMUNITY] [--rejoin]` — stranded detection (CORD-05/06).
     *
     * A Refounding carries only `(newRoot, newEpoch, rotator)` and **no recipient list**, so a
     * member simply left out of the rekey receives nothing and sits on the dead epoch while
     * everyone else moves on. The invite link the membership was joined through is re-minted at
     * the current epoch, so a live bundle there at a **strictly higher** epoch says we were left
     * behind.
     *
     * A bundle is NOT proof of continuity (nothing binds `community_root` to `community_id`), so
     * this verb only reports by default — a link creator must not be able to relocate everyone who
     * joined through their link (CORD-06 §2: the base moves only by a verifiable rekey).
     * `--rejoin` is the user explicitly re-accepting that link: the same trust decision as `join`.
     *
     * Ban-gated at the epoch we are leaving and **fails closed** (no fold, no verdict, no rejoin);
     * a dissolved community is never moved (CORD-02 §9).
     */
    private suspend fun recover(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positionalOrNull(0)
        val rejoin = args.bool("rejoin")
        args.rejectUnknown()
        val store = ConcordStore(dataDir.concordFile)
        val targets =
            if (handle != null) {
                listOf(store.find(handle) ?: return notFound(handle))
            } else {
                store.load()
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val results = mutableListOf<Map<String, Any?>>()
            for (sc in targets) {
                fun skip(reason: String) = mapOf("community_id" to sc.communityId, "name" to sc.name, "stranded" to false, "recovered" to false, "reason" to reason)

                val inviteRef = sc.inviteRef.ifBlank { null }
                if (inviteRef == null) {
                    results += skip("no_invite_ref")
                    continue
                }
                val parsed = ConcordActions.parseInviteLink(inviteRef)
                if (parsed == null) {
                    results += skip("bad_invite_ref")
                    continue
                }
                if (isDissolved(ctx, sc)) {
                    results += skip("dissolved")
                    continue
                }
                val relays = (normalize(parsed.fragment.relays) + normalize(sc.relays)).ifEmpty { ctx.outboxRelays() }
                val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }).map { it.second }
                // Only a LIVE bundle counts: an expired or revoked link is not a rotation we missed.
                val bundle = (ConcordActions.classifyInvite(wraps, parsed.linkSignerPubKey, parsed.fragment.token) as? InviteBundleStatus.Live)?.invite
                if (bundle == null) {
                    results += skip("no_live_bundle")
                    continue
                }

                // Fold the epoch we are leaving to learn whether it banned us. No fold, no verdict.
                val cp = controlPlaneKeysFor(sc)
                ctx.registerConcordStreamKeys(relays, listOfNotNull(cp.signer?.secretKey))
                val controlWraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.address)) }, pendingOnAuthRequired = true).map { it.second }
                val editions = ConcordActions.controlEditions(controlWraps, cp)
                if (editions.isEmpty()) {
                    results += skip("control_plane_not_folded")
                    continue
                }
                val bannedHere = AuthorityResolver.resolve(editions, sc.communityId.hexToByteArray(), sc.owner).isBanned(ctx.signer.pubKey)
                val entry = entryFor(sc)
                if (!ConcordActions.isStranded(entry, bundle, bannedHere)) {
                    results += skip(if (bannedHere) "banned" else "already_current") + ("root_epoch" to sc.rootEpoch)
                    continue
                }
                if (!rejoin) {
                    results +=
                        mapOf(
                            "community_id" to sc.communityId,
                            "name" to sc.name,
                            "stranded" to true,
                            "recovered" to false,
                            "reason" to "rejoin_required",
                            "root_epoch" to sc.rootEpoch,
                            "link_epoch" to bundle.rootEpoch,
                        )
                    continue
                }
                val merged = ConcordActions.rejoinStranded(entry, bundle, bannedHere) ?: continue
                val stored = storedFrom(sc, merged)
                store.upsert(stored)
                announceGuestbookJoin(ctx, stored, bundle.creatorNpub, bundle.label)
                results +=
                    mapOf(
                        "community_id" to sc.communityId,
                        "name" to sc.name,
                        "stranded" to true,
                        "recovered" to true,
                        "from_epoch" to sc.rootEpoch,
                        "root_epoch" to merged.rootEpoch,
                    )
            }
            Output.emit(mapOf("communities" to results))
            return 0
        }
    }

    /**
     * `concord rekey [COMMUNITY]` — follow a Refounding we WERE re-keyed for (CORD-06).
     *
     * The normal counterpart to [recover]: a retained member gets a per-recipient blob on the next
     * epoch's base-rekey plane, and opening it yields the new root. Amethyst drains this on its
     * revision tick; amy has no tick, so it is a verb. Without it a Refounding launched from the CLI
     * strands every other CLI member even though their blob is sitting on the relay.
     *
     * The rotator is authorized against the roster of the epoch being **left** — `hasPermission`,
     * never `effectivePermissions`, so a banned BAN-holder cannot rotate us — and must cite the
     * Grant it acts under (`vac`, CORD-06 §3) at a version that fold has synced; the owner cites
     * nothing. Racing honored rotations converge on the lowest new root. Fails closed: a plane that
     * will not fold yields no verdict and the community is skipped. A dissolved community is never
     * moved (CORD-02 §9).
     */
    private suspend fun rekey(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positionalOrNull(0)
        args.rejectUnknown()
        val store = ConcordStore(dataDir.concordFile)
        val targets = if (handle != null) listOf(store.find(handle) ?: return notFound(handle)) else store.load()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val results = mutableListOf<Map<String, Any?>>()
            for (sc in targets) {
                if (isDissolved(ctx, sc)) {
                    results += mapOf("community_id" to sc.communityId, "name" to sc.name, "rekeyed" to false, "reason" to "dissolved", "root_epoch" to sc.rootEpoch)
                    continue
                }
                val relays = relaysFor(ctx, sc)
                // Authorize against the epoch we are LEAVING — the last plane we can fold.
                val cp = controlPlaneKeysFor(sc)
                ctx.registerConcordStreamKeys(relays, listOfNotNull(cp.signer?.secretKey))
                val controlWraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(cp.address)) }, pendingOnAuthRequired = true).map { it.second }
                val editions = ConcordActions.controlEditions(controlWraps, cp)
                if (editions.isEmpty()) {
                    results += mapOf("community_id" to sc.communityId, "name" to sc.name, "rekeyed" to false, "reason" to "control_plane_not_folded")
                    continue
                }
                val entry = entryFor(sc)
                val authority = AuthorityResolver.resolve(editions, sc.communityId.hexToByteArray(), sc.owner)
                val honored = { r: ReceivedRefounding -> ConcordReceive.isHonoredRotation(entry, editions, authority, r) }

                val baseRekey = ConcordActions.nextBaseRekeyPlane(sc.root.hexToByteArray(), sc.communityId.hexToByteArray(), sc.rootEpoch)
                ctx.registerConcordStreamKeys(relays, listOf(baseRekey.secretKey))
                val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(baseRekey.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
                val received = ConcordActions.openBaseRekey(wraps, baseRekey, ctx.signer, sc.communityId, sc.root.hexToByteArray(), sc.rootEpoch, accept = honored)
                if (received == null) {
                    // Distinguish "no blob at all" from "only rotations we refuse to honor".
                    val any = ConcordActions.openBaseRekey(wraps, baseRekey, ctx.signer, sc.communityId, sc.root.hexToByteArray(), sc.rootEpoch)
                    val reason = if (any == null) "no_blob_for_us" else "unauthorized_rotator"
                    results += mapOf("community_id" to sc.communityId, "name" to sc.name, "rekeyed" to false, "reason" to reason, "root_epoch" to sc.rootEpoch) + (if (any != null) mapOf("rotator" to any.rotator) else emptyMap())
                    continue
                }
                if (received.newEpoch <= sc.rootEpoch) {
                    results += mapOf("community_id" to sc.communityId, "name" to sc.name, "rekeyed" to false, "reason" to "already_current", "root_epoch" to sc.rootEpoch)
                    continue
                }
                val adopted = ConcordReceive.withAdoptedRoot(entry, received.newRoot, received.newEpoch, received.newControlPk, received.newControlRoot)
                // A rotation we adopted supersedes any Refounding of ours still reserved.
                store.upsert(storedFrom(sc, adopted).copy(pendingRefounding = null))
                results += mapOf("community_id" to sc.communityId, "name" to sc.name, "rekeyed" to true, "from_epoch" to sc.rootEpoch, "root_epoch" to received.newEpoch, "rotator" to received.rotator)
            }
            Output.emit(mapOf("communities" to results))
            return 0
        }
    }

    /**
     * This account's CORD-05 Invite List (kind 13303) — the creator's private, self-encrypted record
     * of every link they minted, so a rotation can refresh those links instead of orphaning them.
     * Empty when none was ever published.
     */
    suspend fun readInviteList(ctx: Context): ConcordInviteListDocument? {
        val relays = ctx.outboxRelays()
        if (relays.isEmpty()) return null
        val filter = Filter(kinds = listOf(ConcordInviteListEvent.KIND), authors = listOf(ctx.signer.pubKey))
        // Terminal reasons, not just events: a drain returns nothing both when a relay served us and
        // had nothing AND when nobody answered. Reading the second as "no list yet" is how the
        // read-merge-write below wipes the signer_sk of every link it failed to read.
        val result = ctx.drainResult(relays.associateWith { listOf(filter) })
        val newest =
            result
                .events
                // Filter by kind BEFORE picking the newest — a stray event at this coordinate would
                // otherwise make the list read as unreadable and refuse every later write.
                .mapNotNull { it.second as? ConcordInviteListEvent }
                .maxByOrNull { it.createdAt }
                ?: return if (result.anyRelayServed) ConcordInviteListDocument.EMPTY else null
        return newest.decrypt(ctx.signer)
    }

    /**
     * Merges [patch] into the published list and republishes it, returning the merged document when
     * it landed and null when it did not.
     *
     * Read-merge-write, and **aborts rather than overwriting** when the read fails: kind 13303 is
     * replaceable, so writing a patch-only document over a list we could not read deletes every
     * other link's `signer_sk`. Those secrets cannot be regenerated, and losing one orphans its
     * link at the next rotation, stranding everyone holding that URL.
     *
     * Account-scoped, like the coordinate itself — (13303, me, "") is one list for every community,
     * so reading or writing it on a single community's relays would fork it.
     */
    suspend fun publishInviteList(
        ctx: Context,
        patch: ConcordInviteListDocument,
    ): ConcordInviteListDocument? {
        val relays = ctx.outboxRelays()
        if (relays.isEmpty()) return null
        val base = readInviteList(ctx) ?: return null
        val merged = ConcordInviteList.merge(base, patch)
        val event = ConcordInviteListEvent.create(ctx.signer, merged, TimeUtils.now())
        return if (ctx.publish(event, relays).values.any { it.accepted }) merged else null
    }

    fun notFound(handle: String): Int {
        Output.error("not_found", "no joined community matching '$handle' — run `amy concord list`")
        return 1
    }
}
