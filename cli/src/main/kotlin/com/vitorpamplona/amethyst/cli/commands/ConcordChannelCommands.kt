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
            Output.emit(
                mapOf(
                    "name" to state.metadata?.name,
                    "description" to state.metadata?.description,
                    "icon" to state.metadata?.icon?.let { mapOf("url" to it.url, "key" to it.key, "nonce" to it.nonce, "hash" to it.hash) },
                    "banner" to state.metadata?.banner?.let { mapOf("url" to it.url, "key" to it.key, "nonce" to it.nonce, "hash" to it.hash) },
                    "channels" to
                        state.channels.values.map {
                            mapOf("id" to it.channelIdHex, "name" to it.definition.name, "voice" to it.definition.voice, "private" to it.definition.private)
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
            val channelId = resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val channel = ConcordActions.publicChannel(sc.root.hexToByteArray(), channelId.hexToByteArray(), sc.rootEpoch)
            val wrap = ConcordActions.buildChannelMessage(ctx.signer, channel, channelId, sc.rootEpoch, text, TimeUtils.now())
            val relays = ConcordCommands.relaysFor(ctx, sc)
            // A relay that gates writes behind NIP-42 wants the wrap's author (the stream key) authenticated.
            ctx.registerConcordStreamKeys(relays, listOf(channel.secretKey))
            val ack = ctx.publish(wrap, relays)
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            val acked = ack.filterValues { it }.keys
            Output.emit(mapOf("event_id" to wrap.id, "channel" to channelId, "published_to" to acked.map { it.url }))
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
            val channel = ConcordActions.publicChannel(rootHex.hexToByteArray(), channelId.hexToByteArray(), epoch)
            val relays = ConcordCommands.relaysFor(ctx, sc)
            // The channel plane is NIP-42-gated to its own derived stream key; register it so the drain authenticates.
            ctx.registerConcordStreamKeys(relays, listOf(channel.secretKey))
            val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(channel.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
            val msgs = ConcordActions.channelMessages(wraps, channel, channelId, epoch).takeLast(limit)
            Output.emit(
                mapOf(
                    "channel" to channelId,
                    "epoch" to epoch,
                    "plane" to channel.publicKeyHex,
                    "count" to msgs.size,
                    "messages" to msgs.map { mapOf("event_id" to it.id, "author" to it.author, "content" to it.content, "created_at" to it.createdAt) },
                ),
            )
            return 0
        }
    }

    /** Drain the control plane and fold it into the current community state. */
    private suspend fun foldState(
        ctx: Context,
        sc: StoredCommunity,
    ): ConcordCommunityState {
        val controlPlane = ConcordActions.controlPlane(sc.root.hexToByteArray(), sc.communityId.hexToByteArray(), sc.rootEpoch)
        val relays = ConcordCommands.relaysFor(ctx, sc)
        // The relays gate the plane's kind-1059 behind NIP-42 as the derived stream key — register
        // it so the drain's AUTH challenge is answered as the control plane, not the account.
        ctx.registerConcordStreamKeys(relays, listOf(controlPlane.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(controlPlane.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
        return ConcordActions.foldCommunity(wraps, controlPlane, sc.owner)
    }

    /** Resolve a channel handle: the `general` shortcut, a full hex id, or a folded name/id-prefix match. */
    private suspend fun resolve(
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
