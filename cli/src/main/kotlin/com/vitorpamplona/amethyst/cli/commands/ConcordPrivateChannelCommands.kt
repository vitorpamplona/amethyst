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
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.actions.ConcordPrivateChannels
import com.vitorpamplona.amethyst.commons.actions.ConcordReceive
import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteDraft
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord06Rekey.ChannelRekeyOutcome
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordChannelRekey
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy concord channel create|privatize|publicize|rekey` — Private Channels (CORD-03 §1-2,
 * CORD-06 §1-2). Thin assembly over [ConcordPrivateChannels] (commons); the decisions — the key
 * epoch, the access Role, who a rotation keeps, whether a received rotation is honored — are all
 * shared with Amethyst. Like every amy verb that holds secrets, keys live in the local store only
 * (amy does not republish the Community List).
 */
object ConcordPrivateChannelCommands {
    /** How many channel epochs `privatize` probes for earlier rotations (Armada MAX_PROBED_CHANNEL_EPOCH). */
    private const val MAX_PROBED_CHANNEL_EPOCH = 32L

    suspend fun channel(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "concord channel",
            tail,
            "concord channel <create|privatize|publicize|rekey>",
            routes =
                mapOf(
                    "create" to { rest -> create(dataDir, rest) },
                    "privatize" to { rest -> privatize(dataDir, rest) },
                    "publicize" to { rest -> publicize(dataDir, rest) },
                    "rekey" to { rest -> rekey(dataDir, rest) },
                ),
        )

    /** Publishes [wraps] in order to [sc]'s relays; the first one no relay takes stops the run. */
    private suspend fun publishAll(
        ctx: Context,
        sc: StoredCommunity,
        wraps: List<Event>,
    ): Int? {
        val relays = ConcordCommands.relaysFor(ctx, sc)
        for (wrap in wraps) {
            val ack = ctx.publish(wrap, relays)
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
        }
        return null
    }

    private fun canManageChannels(
        authority: AuthorityResolver,
        me: HexKey,
    ): Boolean = authority.isOwner(me) || authority.hasPermission(me, ConcordPermissions.MANAGE_CHANNELS)

    private fun forbidden(): Int = Output.error("forbidden", "this needs the Manage-channels permission (CORD-03 §2); readers would drop the edition")

    /** Stores [sc] with the Private Channel [key] (refused when it would not move the channel forward). */
    private fun storeKey(
        store: ConcordStore,
        sc: StoredCommunity,
        key: PrivateChannelKey,
    ): Boolean {
        val fresh = store.load().firstOrNull { it.communityId == sc.communityId } ?: sc
        val next = ConcordChannelKeyring.withChannelKey(ConcordCommands.entryFor(fresh), key) ?: return false
        store.upsert(ConcordCommands.storedFrom(fresh, next))
        return true
    }

    /** `concord channel create COMMUNITY NAME [--private [--role NAME]]`. */
    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val name = args.positional(1, "name")
        val private = args.bool("private")
        val roleName = args.flag("role")
        args.rejectUnknown()
        if (!ChannelEntity(name = name.trim()).hasValidName()) return Output.error("bad_args", "a channel name must be 1..${ChannelEntity.NAME_MAX_BYTES} UTF-8 bytes").let { 2 }
        if (roleName != null && !private) return Output.error("bad_args", "--role names a Private channel's access Role; add --private").let { 2 }
        val store = ConcordStore(dataDir.concordFile)
        val sc = store.find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val loaded = ConcordModCommands.load(ctx, sc, dataDir)
            val (cp, editions) = loaded
            ConcordModCommands.writeGuard(cp)?.let { return it }
            val authority = AuthorityResolver.resolve(editions, sc.communityId.hexToByteArray(), sc.owner)
            if (!canManageChannels(authority, ctx.signer.pubKey)) return forbidden()

            if (!private) {
                val channelId = RandomInstance.bytes(32)
                val wrap = ConcordModeration.defineChannel(ctx.signer, cp, sc.communityId.hexToByteArray(), channelId, ChannelEntity(name = name.trim()), editions, TimeUtils.now(), owner = sc.owner)
                publishAll(ctx, sc, listOf(wrap))?.let { return it }
                Output.emit(mapOf("channel_id" to channelId.toHexKey(), "name" to name.trim(), "private" to false))
                return 0
            }

            val build =
                ConcordPrivateChannels.create(ctx.signer, cp, sc.communityId.hexToByteArray(), name, roleName, editions, authority, sc.owner, TimeUtils.now())
                    ?: return Output.error("forbidden", "no rank to mint this channel's access Role from")
            // The key goes into the store BEFORE the editions publish: otherwise a crash orphans the only copy.
            if (!storeKey(store, loaded.community, build.key)) return Output.error("conflict", "could not store the new channel key")
            publishAll(ctx, sc, build.wraps)?.let { return it }
            Output.emit(
                mapOf(
                    "channel_id" to build.channelIdHex,
                    "name" to name.trim(),
                    "private" to true,
                    "channel_epoch" to build.key.epoch,
                    "access_role_id" to build.roleIdHex,
                ),
            )
            return 0
        }
    }

    /** `concord channel privatize COMMUNITY CHANNEL [--role NAME]`. */
    private suspend fun privatize(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val roleName = args.flag("role")
        args.rejectUnknown()
        val store = ConcordStore(dataDir.concordFile)
        val sc = store.find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val loaded = ConcordModCommands.load(ctx, sc, dataDir)
            val (cp, editions) = loaded
            ConcordModCommands.writeGuard(cp)?.let { return it }
            val state = ConcordCommunityState.fold(editions, sc.communityId.hexToByteArray(), sc.owner)
            if (!canManageChannels(state.authority, ctx.signer.pubKey)) return forbidden()
            val standing = state.channels[channelId]?.definition ?: return Output.error("not_found", "channel '$channelRef' is not in the folded Control Plane")
            if (standing.private) return Output.error("already_private", "channel '$channelRef' is already private")

            // The next channel epoch must climb past every generation ever used — including ones this
            // account never held — so probe the rekey addresses the roots derive (CORD-03 §2).
            val entry = ConcordCommands.entryFor(loaded.community)
            val window = HashMap<HexKey, Long>()
            for (root in (listOf(entry.root) + entry.heldRoots.map { it.key }).distinct()) {
                for (epoch in 1L..MAX_PROBED_CHANNEL_EPOCH) window[ConcordChannelRekey.address(root.hexToByteArray(), channelId.hexToByteArray(), epoch).publicKeyHex] = epoch
            }
            val relays = ConcordCommands.relaysFor(ctx, sc)
            val seen = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilterFor(window.keys.toList())) }).map { it.second }
            val floor = seen.mapNotNull { window[it.pubKey] }.maxOrNull() ?: 0
            if (floor >= MAX_PROBED_CHANNEL_EPOCH) return Output.error("inconclusive", "this channel has rotated at least $MAX_PROBED_CHANNEL_EPOCH times; its next epoch can't be established safely")

            val build =
                ConcordPrivateChannels.privatize(ctx.signer, cp, entry, channelId, standing, roleName, editions, state.authority, TimeUtils.now(), floor)
                    ?: return Output.error("forbidden", "no rank to mint this channel's access Role from")
            if (!storeKey(store, loaded.community, build.key)) return Output.error("conflict", "could not store the new channel key")
            publishAll(ctx, sc, build.wraps)?.let { return it }
            Output.emit(mapOf("channel_id" to channelId, "private" to true, "channel_epoch" to build.key.epoch, "access_role_id" to build.roleIdHex))
            return 0
        }
    }

    /** `concord channel publicize COMMUNITY CHANNEL`. */
    private suspend fun publicize(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val (cp, editions) = ConcordModCommands.load(ctx, sc, dataDir)
            ConcordModCommands.writeGuard(cp)?.let { return it }
            val state = ConcordCommunityState.fold(editions, sc.communityId.hexToByteArray(), sc.owner)
            if (!canManageChannels(state.authority, ctx.signer.pubKey)) return forbidden()
            val standing = state.channels[channelId]?.definition ?: return Output.error("not_found", "channel '$channelRef' is not in the folded Control Plane")
            val wrap =
                ConcordPrivateChannels.publicize(ctx.signer, cp, sc.communityId.hexToByteArray(), channelId, standing, editions, sc.owner, TimeUtils.now())
                    ?: return Output.error("already_public", "channel '$channelRef' is not private")
            publishAll(ctx, sc, listOf(wrap))?.let { return it }
            Output.emit(mapOf("channel_id" to channelId, "private" to false))
            return 0
        }
    }

    /** `concord channel rekey COMMUNITY CHANNEL` — rotate to exactly the members entitled today. */
    private suspend fun rekey(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        args.rejectUnknown()
        val store = ConcordStore(dataDir.concordFile)
        val sc = store.find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            if (ConcordCommands.isDissolved(ctx, sc)) return Output.error("dissolved", "community '$handle' has been dissolved (CORD-02 §9)")
            val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val loaded = ConcordModCommands.load(ctx, sc, dataDir)
            val authority = AuthorityResolver.resolve(loaded.editions, sc.communityId.hexToByteArray(), sc.owner)
            // The known role holders a rotation to the entitled set leaves out; a roleless member ranks
            // last, so any MANAGE_CHANNELS holder outranks them and they need no check.
            val keep = ConcordPrivateChannels.keepSet(authority, channelId, ctx.signer.pubKey)
            val cut = (authority.roleHolders() + authority.owner()).filterTo(HashSet()) { it !in keep }
            return rotate(ctx, store, loaded.community, channelId, authority, loaded.editions, cut)
        }
    }

    /** A rotation's result: [error] (code to message) when refused or unpublished, else what landed. */
    internal class Rotation(
        val error: Pair<String, String>? = null,
        val newEpoch: Long = 0,
        val kept: Int = 0,
        val chunks: Int = 0,
    )

    /** [rotateSilently] with its outcome emitted as the command's JSON line. */
    internal suspend fun rotate(
        ctx: Context,
        store: ConcordStore,
        sc: StoredCommunity,
        channelId: HexKey,
        authority: AuthorityResolver,
        editions: List<ControlEdition>,
        cut: Set<HexKey>,
    ): Int {
        val r = rotateSilently(ctx, store, sc, channelId, authority, editions, cut)
        r.error?.let { (code, message) -> return Output.error(code, message) }
        Output.emit(mapOf("channel_id" to channelId, "rekeyed" to true, "channel_epoch" to r.newEpoch, "kept" to r.kept, "chunks" to r.chunks))
        return 0
    }

    /**
     * Rotates [channelId] to its entitled set (CORD-06 §1-2), cutting [cut]: authority checked, the
     * key reserved in the store before anything publishes (a retry re-delivers the same key), every
     * chunk accepted by a relay before the new key is adopted locally. Prints nothing.
     */
    internal suspend fun rotateSilently(
        ctx: Context,
        store: ConcordStore,
        sc: StoredCommunity,
        channelId: HexKey,
        authority: AuthorityResolver,
        editions: List<ControlEdition>,
        cut: Set<HexKey>,
    ): Rotation {
        val me = ctx.signer.pubKey
        val entry = ConcordCommands.entryFor(sc)
        val held = ConcordChannelKeyring.heldKey(entry, channelId) ?: return Rotation("no_channel_key" to "this account holds no key for channel $channelId, so it cannot rotate it")
        if (!ConcordPrivateChannels.canRotate(authority, me, cut)) {
            return Rotation("forbidden" to "rotating needs the Manage-channels permission and outranking every member it cuts (CORD-06 §3)")
        }
        val citation = ConcordReceive.rotationCitation(entry, editions, me)
        if (citation == null && !authority.isOwner(me)) return Rotation("forbidden" to "no Grant of ours to cite; nobody would honor this rotation (CORD-06 §3)")

        val newEpoch = held.epoch + 1
        val reservation = "${held.channelId.lowercase()}:$newEpoch:${ConcordChannelRekey.prevCommit(held.epoch, held.key.hexToByteArray())}"
        val newKeyHex = sc.pendingChannelRotations[reservation] ?: ConcordChannelRekey.mintKey().toHexKey()
        store.upsert(sc.copy(pendingChannelRotations = sc.pendingChannelRotations + (reservation to newKeyHex)))

        val keep = ConcordPrivateChannels.keepSet(authority, channelId, me)
        val wraps = ConcordPrivateChannels.buildRotation(ctx.signer, sc.root.hexToByteArray(), held, newKeyHex.hexToByteArray(), keep, TimeUtils.now(), citation)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        for (wrap in wraps) {
            if (ctx.publish(wrap, relays).values.none { it.accepted }) {
                return Rotation("rejected" to "no relay accepted chunk ${wrap.id} of the rotation; re-running re-delivers the same key")
            }
        }

        // Adopt at once: the rotator must never keep writing under the severed key.
        val fresh = store.load().firstOrNull { it.communityId == sc.communityId } ?: sc
        val next = ConcordChannelKeyring.withRotatedKey(ConcordCommands.entryFor(fresh), channelId, newKeyHex, newEpoch)
        if (next != null) store.upsert(ConcordCommands.storedFrom(fresh, next).copy(pendingChannelRotations = fresh.pendingChannelRotations - reservation))
        return Rotation(newEpoch = newEpoch, kept = keep.size, chunks = wraps.size)
    }

    /**
     * Follows every held Private Channel's rotations for [sc] (CORD-06 §2): drains the watched
     * channel-rekey addresses, adopts a key carried off the one we hold, or drops the channel (and
     * records the cut) when a rotation from someone who outranks us left us out. Returns one result
     * per channel acted on.
     */
    internal suspend fun drainChannelRekeys(
        ctx: Context,
        store: ConcordStore,
        sc: StoredCommunity,
    ): List<Map<String, Any?>> {
        val entry = ConcordCommands.entryFor(sc)
        val keys = ConcordPrivateChannels.watchKeys(entry)
        if (keys.isEmpty()) return emptyList()
        val relays = ConcordCommands.relaysFor(ctx, sc)
        ctx.registerConcordStreamKeys(relays, keys.values.map { it.secretKey })
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilterFor(keys.keys.toList())) }, pendingOnAuthRequired = true).map { it.second }
        if (wraps.isEmpty()) return emptyList()
        val loaded = ConcordModCommands.load(ctx, sc)
        val authority = AuthorityResolver.resolve(loaded.editions, sc.communityId.hexToByteArray(), sc.owner)
        val outcomes = ConcordPrivateChannels.receive(entry, wraps, loaded.editions, authority, ctx.signer)
        if (outcomes.isEmpty()) return emptyList()
        val fresh = store.load().firstOrNull { it.communityId == sc.communityId } ?: sc
        val next = ConcordPrivateChannels.applyOutcome(ConcordCommands.entryFor(fresh), outcomes, entry.privateChannels.associate { it.channelId.lowercase() to it.epoch })
        if (next != null) store.upsert(ConcordCommands.storedFrom(fresh, next))
        return outcomes.map { (id, outcome) ->
            when (outcome) {
                is ChannelRekeyOutcome.Adopted -> mapOf("community_id" to sc.communityId, "channel_id" to id, "adopted" to true, "channel_epoch" to outcome.epoch)
                is ChannelRekeyOutcome.Removed -> mapOf("community_id" to sc.communityId, "channel_id" to id, "removed" to true, "channel_epoch" to outcome.epoch)
                ChannelRekeyOutcome.None -> mapOf("community_id" to sc.communityId, "channel_id" to id)
            }
        }
    }

    /**
     * After a Grant: vends every Private Channel it opened to its member by Direct Invite (only those
     * channels), and rotates every one it closed (CORD-03/06; Armada `handleToggleRole`). Returns what
     * it did, for the grant command's output.
     */
    internal suspend fun reconcileAccess(
        ctx: Context,
        store: ConcordStore,
        sc: StoredCommunity,
        before: AuthorityResolver,
        afterEditions: List<ControlEdition>,
    ): Map<String, Any?> {
        val state = ConcordCommunityState.fold(afterEditions, sc.communityId.hexToByteArray(), sc.owner)
        val me = ctx.signer.pubKey.lowercase()
        val entry = ConcordCommands.entryFor(sc)
        val changes = ConcordInviteVend.accessChanges(before, state.authority, state.privateChannelIds)
        val vended = mutableListOf<Map<String, Any?>>()
        val rotated = mutableListOf<String>()
        val unrotated = mutableListOf<Map<String, String>>()
        val unheld = mutableListOf<String>()
        val byMember = HashMap<HexKey, MutableSet<HexKey>>()
        for (change in changes) {
            if (ConcordChannelKeyring.heldKey(entry, change.channelIdHex) == null) {
                unheld += change.channelIdHex
                continue
            }
            for (m in change.gained - me) byMember.getOrPut(m) { HashSet() }.add(change.channelIdHex)
            val cut = change.lost - me
            if (cut.isNotEmpty()) {
                val fresh = store.load().firstOrNull { it.communityId == sc.communityId } ?: sc
                val r = rotateSilently(ctx, store, fresh, change.channelIdHex, state.authority, afterEditions, cut)
                if (r.error == null) rotated += change.channelIdHex else unrotated += mapOf("channel_id" to change.channelIdHex, "reason" to r.error.second)
            }
        }
        for ((member, channels) in byMember) {
            val draft = ConcordActions.draftDirectInvite(entry, state, me, member, onlyChannelIds = channels) as? ConcordDirectInviteDraft.Ready ?: continue
            val wrap = ConcordActions.buildDirectInvite(ctx.signer, member, draft.invite)
            val lists = ctx.cachedRelayListsOf(member) ?: RecipientRelayFetcher.fetchRelayLists(ctx.client, member, ctx.bootstrapRelays())
            val ack = ctx.publish(wrap, ConcordActions.directInviteDeliveryRelays(lists))
            vended += mapOf("member" to member, "channels" to draft.invite.channels.map { it.id }, "delivered" to ack.values.any { it.accepted })
        }
        return mapOf("channel_keys_vended" to vended, "channels_rotated" to rotated, "channels_not_rotated" to unrotated, "channels_not_held" to unheld)
    }
}
