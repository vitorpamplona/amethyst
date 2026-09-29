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
import com.vitorpamplona.amethyst.commons.actions.ChannelPlane
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import com.vitorpamplona.amethyst.commons.actions.ConcordPinContext
import com.vitorpamplona.amethyst.commons.actions.ConcordPinEvidence
import com.vitorpamplona.amethyst.commons.actions.ConcordPinOutcome
import com.vitorpamplona.amethyst.commons.actions.ConcordPinWrite
import com.vitorpamplona.amethyst.commons.actions.ConcordPinning
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy concord pins|pin|unpin` — a Channel's Pin List (CORD-04 §7). Thin assembly: the drain is
 * here, the reading, verification, gating, caps and edition building are [ConcordPinning]'s.
 */
object ConcordPinCommands {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** One channel's drained view: its planes, the wraps on them, and the evidence they carry. */
    private class ChannelView(
        val planes: List<ChannelPlane>,
        val wraps: List<Event>,
        val evidence: ConcordPinEvidence,
    )

    /** Drains every plane of [channelIdHex] this account holds (current + held prior epochs). */
    private suspend fun drainChannel(
        ctx: Context,
        sc: StoredCommunity,
        state: ConcordCommunityState,
        channelIdHex: String,
    ): ChannelView {
        val entry = ConcordCommands.entryFor(sc)
        val isPrivate = state.channels[channelIdHex]?.definition?.private == true
        val planes = listOfNotNull(ConcordActions.currentChannelPlane(entry, channelIdHex, isPrivate)) + ConcordActions.historicalChannelPlanes(entry, channelIdHex, isPrivate)
        if (planes.isEmpty()) return ChannelView(planes, emptyList(), ConcordPinEvidence(emptyList()))
        val relays = ConcordCommands.relaysFor(ctx, sc)
        ctx.registerConcordStreamKeys(relays, planes.map { it.key.secretKey })
        val filter = ConcordActions.planeFilterFor(planes.map { it.key.publicKeyHex })
        val wraps = ctx.drain(relays.associateWith { listOf(filter) }, pendingOnAuthRequired = true).map { it.second }
        val byAddress = planes.associateBy { it.key.publicKeyHex }
        val rumors = wraps.mapNotNull { wrap -> byAddress[wrap.pubKey]?.let { ConcordActions.openChannelRumor(wrap, it.key, channelIdHex, it.epoch) } }
        return ChannelView(planes, wraps, ConcordPinEvidence(rumors))
    }

    private fun read(
        sc: StoredCommunity,
        editions: List<ControlEdition>,
        channelIdHex: String,
        view: ChannelView,
    ): ConcordChannelPins {
        val head = ConcordPinning.headFor(editions, sc.communityId, sc.owner, channelIdHex)
        return ConcordPinning.read(
            head,
            channelIdHex,
            unsealKey = { epoch ->
                view.planes
                    .firstOrNull { it.epoch == epoch }
                    ?.key
                    ?.conversationKey
            },
            isKilled = view.evidence::isKilled,
            newestEdit = view.evidence::newestEdit,
        )
    }

    private fun render(pins: ConcordChannelPins): Map<String, Any?> =
        mapOf(
            "channel" to pins.channelIdHex,
            "version" to pins.head?.version,
            "count" to pins.count,
            // Unreadable is not empty: the list is sealed under an epoch key this account never held.
            "sealed_unavailable" to pins.sealedUnavailable,
            "sealed" to pins.sealedForm,
            "violating" to pins.violating,
            "invalid_entries" to pins.invalidEntries,
            "deleted" to pins.killed.map { it.rumorId },
            "pins" to
                pins.pins.map {
                    mapOf(
                        "rumor_id" to it.rumorId,
                        "author" to it.author,
                        "kind" to it.pin.kind,
                        "content" to it.content,
                        "created_at" to it.pin.createdAt,
                        "edited" to it.edited,
                        // A newer Edit this account holds but the entry cannot prove yet.
                        "stale_edit" to (it.newerEdit != null),
                        "epoch" to it.pin.epoch,
                        "wrap" to it.pin.wrapHint,
                    )
                },
        )

    /** `concord pins COMMUNITY CHANNEL` — the verified Pin List. */
    suspend fun pins(
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
            val loaded = ConcordModCommands.load(ctx, sc)
            val state = ConcordCommunityState.fold(loaded.editions, sc.communityId.hexToByteArray(), sc.owner)
            val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            if (channelId !in state.channels) return Output.error("not_found", "channel '$channelRef' is not folded")
            val view = drainChannel(ctx, sc, state, channelId)
            Output.emit(render(read(sc, loaded.editions, channelId, view)))
            return 0
        }
    }

    /** `concord pin COMMUNITY CHANNEL RUMOR_ID` */
    suspend fun pin(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = write(dataDir, rest, pin = true)

    /** `concord unpin COMMUNITY CHANNEL RUMOR_ID` */
    suspend fun unpin(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int = write(dataDir, rest, pin = false)

    private suspend fun write(
        dataDir: DataDir,
        rest: Array<String>,
        pin: Boolean,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val rumorId = args.positional(2, "rumor_id").lowercase()
        args.rejectUnknown()
        if (!HEX64.matches(rumorId)) return Output.error("bad_args", "RUMOR_ID must be a 64-char hex rumor id")
        val stored = ConcordStore(dataDir.concordFile).find(handle) ?: return ConcordCommands.notFound(handle)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            // CORD-02 §9: after Dissolution no edition can land.
            if (ConcordCommands.isDissolved(ctx, stored)) return Output.error("dissolved", "community '$handle' has been dissolved and is read-only (CORD-02 §9)")
            val loaded = ConcordModCommands.load(ctx, stored, dataDir)
            val sc = loaded.community
            ConcordModCommands.writeGuard(loaded.keys)?.let { return it }
            val communityId = sc.communityId.hexToByteArray()
            val state = ConcordCommunityState.fold(loaded.editions, communityId, sc.owner)
            val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return Output.error("not_found", "no channel '$channelRef'")
            val definition = state.channels[channelId]?.definition ?: return Output.error("not_found", "channel '$channelRef' is not folded")
            val view = drainChannel(ctx, sc, state, channelId)
            val me = ctx.signer.pubKey
            val pinCtx =
                ConcordPinContext(
                    actor = ctx.signer,
                    controlPlane = loaded.keys,
                    communityId = communityId,
                    owner = sc.owner,
                    current = loaded.editions,
                    channelIdHex = channelId,
                    channelIsPrivate = definition.private,
                    currentPlane = ConcordActions.currentChannelPlane(ConcordCommands.entryFor(sc), state, channelId),
                    pins = read(sc, loaded.editions, channelId, view),
                    authorized = sc.owner.equals(me, ignoreCase = true) || state.authority.hasPermission(me, ConcordPermissions.PIN_MESSAGES),
                )
            val result =
                if (pin) {
                    val refused = ConcordPinning.refusal(pinCtx)
                    val source = if (refused == null) ConcordPinning.sourceFrom(view.wraps, view.planes, rumorId) else null
                    when {
                        refused != null -> ConcordPinWrite(refused)
                        source == null -> ConcordPinWrite(ConcordPinOutcome.MESSAGE_UNAVAILABLE)
                        else -> ConcordPinning.pin(pinCtx, source, TimeUtils.now())
                    }
                } else {
                    ConcordPinning.unpin(pinCtx, rumorId, TimeUtils.now())
                }
            val wrap = result.wrap ?: return Output.error(result.outcome.name.lowercase(), refusalDetail(result.outcome))
            val ack = ctx.publish(wrap, ConcordCommands.relaysFor(ctx, sc))
            RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
            Output.emit(mapOf("channel" to channelId, "rumor_id" to rumorId, "pinned" to pin, "entries" to result.entries.size, "event_id" to wrap.id) + RawEventSupport.ackFields(ack))
            return 0
        }
    }

    private fun refusalDetail(outcome: ConcordPinOutcome): String =
        when (outcome) {
            ConcordPinOutcome.ALREADY_PINNED -> "that message is already pinned"
            ConcordPinOutcome.NOT_PINNED -> "that message is not pinned"
            ConcordPinOutcome.NOT_AUTHORIZED -> "pinning takes PIN_MESSAGES (or ownership) (CORD-04 §3)"
            ConcordPinOutcome.NO_WRITE_KEY -> "no control_root held for this epoch (CORD-02 §2)"
            ConcordPinOutcome.NO_CHANNEL_KEY -> "private channel and this account holds no key for it, so the list cannot be sealed"
            ConcordPinOutcome.LIST_UNAVAILABLE -> "the Pin List is sealed under a key this account never held; writing would drop pins it cannot see (CORD-04 §7)"
            ConcordPinOutcome.MESSAGE_UNAVAILABLE -> "no held wrap carries that rumor, so its seal cannot be proven"
            ConcordPinOutcome.UNVERIFIABLE -> "the message would not verify as a pin (only kind 9 / 1111 messages can be pinned)"
            ConcordPinOutcome.TOO_MANY_PINS -> "the list already has 25 pins; unpin one first"
            ConcordPinOutcome.TOO_LARGE -> "the list would exceed 32,768 bytes; unpin one first"
            else -> outcome.name.lowercase()
        }
}
