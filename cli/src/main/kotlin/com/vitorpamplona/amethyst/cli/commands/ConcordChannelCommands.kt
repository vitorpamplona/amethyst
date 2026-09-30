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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.TimeUtils

/** `amy concord channels|send|read` — the per-channel chat verbs. */
object ConcordChannelCommands {
    private val HEX64 = Regex("[0-9a-fA-F]{64}")

    suspend fun channels(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val state = foldState(ctx, sc)
            ConcordCommands.kickedGuard(ctx, dataDir, sc, state.authority)?.let { return it }
            Output.emit(
                mapOf(
                    "name" to state.metadata?.name,
                    "description" to state.metadata?.description,
                    // CORD-02 §9: once dissolved the community is sealed read-only (history only, no new posts).
                    "dissolved" to state.dissolved,
                    "icon" to state.metadata?.icon?.let { mapOf("url" to it.url, "key" to it.key, "nonce" to it.nonce, "hash" to it.hash) },
                    "banner" to state.metadata?.banner?.let { mapOf("url" to it.url, "key" to it.key, "nonce" to it.nonce, "hash" to it.hash) },
                    "channels" to
                        state.channels.values.map {
                            mapOf(
                                "id" to it.channelIdHex,
                                "name" to it.definition.name,
                                "private" to it.definition.private,
                                // False for a Private Channel whose key this account does not hold (CORD-03 §1).
                                "readable" to ConcordActions.canAccessChannel(ConcordCommands.entryFor(sc), state, it.channelIdHex),
                            )
                        },
                ),
            )
            return 0
        }
    }

    suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val text = args.positional(2, "text")
        args.rejectUnknown()
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // CORD-02 §9: a dissolved community is sealed read-only — held keys still open history, but
            // nothing new is honored, so refuse to post before we ever build/publish a wrap.
            val state = foldState(ctx, sc)
            ConcordCommands.kickedGuard(ctx, dataDir, sc, state.authority)?.let { return it }
            if (state.dissolved) {
                return Output.error("dissolved", "community '$handle' has been dissolved and is read-only (CORD-02 §9)")
            }
            // CORD-04 §4: every reader drops a banned author's messages, so a post would vanish unseen.
            if (state.authority.isBanned(ctx.signer.pubKey)) {
                return Output.error("banned", "this account is banned from '$handle' (CORD-04 §4); its messages are hidden from everyone")
            }
            val channelId = resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            // The channel's own plane (CORD-03 §1): root-derived when Public, its held key when
            // Private — and a refusal, never the root plane, for a Private Channel we hold no key for.
            val plane =
                ConcordActions.currentChannelPlane(ConcordCommands.entryFor(sc), state, channelId)
                    ?: return Output.error("no_channel_key", "channel '$channelRef' is not folded, or is private and this account holds no key for it (CORD-03 §1)")
            val channel = plane.key
            // CORD-08 §2: the folded timer rides inside the signed rumor, and on the wrap for relays.
            val wrap = ConcordActions.buildChannelMessage(ctx.signer, channel, channelId, plane.epoch, text, TimeUtils.now(), timerSecs = state.metadata?.messageExpirationSecs())
            val relays = ConcordCommands.relaysFor(ctx, sc)
            // A relay that gates writes behind NIP-42 wants the wrap's author (the stream key) authenticated.
            ctx.registerConcordStreamKeys(relays, listOf(channel.secretKey))
            val ack = ctx.publish(wrap, relays)
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            Output.emit(mapOf("event_id" to wrap.id, "channel" to channelId) + RawEventSupport.ackFields(ack))
            return 0
        }
    }

    suspend fun read(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val limit = args.intFlag("limit", 50)
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        // Diagnostic overrides (concord-epoch-walking-backfill): read a PRIOR epoch's Chat Plane by
        // supplying that epoch's community_root. A Refounding (CORD-06 §3) rotates the root and bumps
        // the epoch, so pre-refounding messages live under a different derived stream key that the
        // normal read (current epoch only) never fetches. Both derive from the same channel id, which
        // is epoch-invariant, so channel resolution stays on the current epoch below.
        val epoch = args.longFlag("epoch", sc.rootEpoch)
        // Resolve the root for that epoch: explicit --root wins; else the current root if --epoch is
        // the current epoch; else a stored heldRoot for that epoch (populated by `amy concord import`).
        val rootHex =
            args.flag("root")
                ?: sc.root.takeIf { epoch == sc.rootEpoch }
                ?: sc.heldRoots.firstOrNull { it.epoch == epoch }?.root
                ?: return Output
                    .error("not_found", "no root known for epoch $epoch — pass --root <hex> or run `amy concord import` to load heldRoots")
                    .let { 1 }
        if (!HEX64.matches(rootHex)) return Output.error("bad_args", "--root must be a 64-char hex community_root").let { 2 }
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val channelId = resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val state = foldState(ctx, sc)
            ConcordCommands.kickedGuard(ctx, dataDir, sc, state.authority)?.let { return it }
            // A Private Channel is read only on its own key's plane (CORD-03 §1); --root/--epoch pick a
            // root-derived plane and so apply to Public Channels only.
            val privatePlane =
                if (state.channels[channelId]?.definition?.private == true) {
                    ConcordActions.currentChannelPlane(ConcordCommands.entryFor(sc), state, channelId)
                        ?: return Output.error("no_channel_key", "channel '$channelRef' is private and this account holds no key for it (CORD-03 §1)")
                } else {
                    null
                }
            val channel = privatePlane?.key ?: ConcordActions.publicChannel(rootHex.hexToByteArray(), channelId.hexToByteArray(), epoch)

            @Suppress("NAME_SHADOWING")
            val epoch = privatePlane?.epoch ?: epoch
            val relays = ConcordCommands.relaysFor(ctx, sc)
            // The channel plane is NIP-42-gated to its own derived stream key; register it so the drain authenticates.
            ctx.registerConcordStreamKeys(relays, listOf(channel.secretKey))
            val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(channel.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
            // A banned member's messages are hidden, as every client shows the channel (CORD-04 §4);
            // the count of what was hidden stays in the output so interop checks can see it arrived.
            val (banned, visible) = ConcordActions.channelMessages(wraps, channel, channelId, epoch).partition { state.authority.isBanned(it.author) }
            val msgs = visible.takeLast(limit)
            Output.emit(
                mapOf(
                    "channel" to channelId,
                    "epoch" to epoch,
                    "plane" to channel.publicKeyHex,
                    "count" to msgs.size,
                    "hidden_banned" to banned.size,
                    "messages" to msgs.map { mapOf("event_id" to it.id, "author" to it.author, "content" to it.content, "created_at" to it.createdAt) },
                ),
            )
            return 0
        }
    }

    /** Drain the control plane and fold it into the current community state. */
    suspend fun foldState(
        ctx: Context,
        sc: StoredCommunity,
    ): ConcordCommunityState {
        val controlPlane = ConcordCommands.controlPlaneKeysFor(sc)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        // The dissolution tombstone lives at its own id-derived address (CORD-02 §9), drained alongside.
        val dissolved = ConcordDissolution.planeKey(sc.communityId)
        val dissolvedAddress = dissolved.publicKeyHex
        // The relays gate each plane's kind-1059 behind NIP-42 as the stream key — register them so
        // the drain's AUTH challenge is answered as the plane, not the account. On a split epoch
        // only staff hold the control secret (CORD-02 §2); a plain member relies on the relay
        // serving that plane unauthenticated. The dissolved plane's key derives from the public
        // community id, so every member can always answer for it.
        ctx.registerConcordStreamKeys(relays, listOfNotNull(controlPlane.signer?.secretKey, dissolved.secretKey))
        val wraps =
            ctx
                .drain(relays.associateWith { listOf(ConcordActions.planeFilterFor(listOf(controlPlane.address, dissolvedAddress))) }, pendingOnAuthRequired = true)
                .map { it.second }
        val (graveWraps, controlWraps) = wraps.partition { it.pubKey == dissolvedAddress }
        return ConcordActions
            .foldCommunity(controlWraps, controlPlane, sc.communityId.hexToByteArray(), sc.owner)
            .withDissolved(ConcordDissolution.isDissolved(graveWraps, sc.communityId, sc.owner))
    }

    /** Resolve a channel handle: the `general` shortcut, a full hex id, or a folded name/id-prefix match. */
    internal suspend fun resolve(
        ctx: Context,
        sc: StoredCommunity,
        ref: String,
    ): String? {
        if (ref == "general" && sc.generalChannelId.isNotBlank()) return sc.generalChannelId
        if (HEX64.matches(ref)) return ref
        val state = foldState(ctx, sc)
        return state.channels.values
            .firstOrNull { it.definition.name.equals(ref, ignoreCase = true) }
            ?.channelIdHex
            ?: state.channels.values
                .firstOrNull { it.channelIdHex.startsWith(ref) }
                ?.channelIdHex
    }
}
