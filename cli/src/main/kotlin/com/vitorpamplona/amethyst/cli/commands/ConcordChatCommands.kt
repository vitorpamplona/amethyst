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
import com.vitorpamplona.amethyst.commons.actions.ChannelPlane
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitations
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * `amy concord edit|delete|react|hide` — what a member does to a message once it is posted, the
 * same rumors the app's message menu writes ([ConcordActions] builds them, Quartz's
 * [com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat] shapes them):
 *
 *  - `edit`: a kind-3302 edit of one of our own messages (only the author's edits apply);
 *  - `delete`: a kind-5 delete of our own messages or reactions;
 *  - `react`: a kind-7 reaction (`+` unless an emoji is given);
 *  - `hide`: a moderator's kind-5 hide of other members' messages, citing the Grant that gives us
 *    MANAGE_MESSAGES (CORD-03 §3); refused unless every reader's fold would honor it.
 *
 * Targets are rumor ids (as `amy concord read` prints them) on the channel's current plane.
 */
object ConcordChatCommands {
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /** A channel opened for a write: its plane, the relays it lives on, and the rumors it holds now. */
    private class OpenChannel(
        val state: ConcordCommunityState,
        val channelId: String,
        val plane: ChannelPlane,
        val relays: Set<NormalizedRelayUrl>,
        val rumors: List<Event>,
    ) {
        fun rumor(id: String): Event? = rumors.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    /**
     * Folds [handle]'s community, refuses what a write can't do (kicked, dissolved, banned, a channel
     * we hold no key for) and opens [channelRef]'s current plane. Returns the channel, or the exit code.
     */
    private suspend fun open(
        ctx: Context,
        dataDir: DataDir,
        handle: String,
        channelRef: String,
    ): Pair<OpenChannel?, Int> {
        val sc = ConcordStore(dataDir.concordFile).find(handle) ?: return null to ConcordCommands.notFound(handle)
        val state = ConcordChannelCommands.foldState(ctx, sc)
        ConcordCommands.kickedGuard(ctx, dataDir, sc, state.authority)?.let { return null to it }
        if (state.dissolved) {
            return null to Output.error("dissolved", "community '$handle' has been dissolved and is read-only (CORD-02 §9)")
        }
        if (state.authority.isBanned(ctx.signer.pubKey)) {
            return null to Output.error("banned", "this account is banned from '$handle' (CORD-04 §4); its messages are hidden from everyone")
        }
        val channelId = ConcordChannelCommands.resolve(ctx, sc, channelRef) ?: return null to Output.error("not_found", "no channel '$channelRef'")
        val plane =
            ConcordActions.currentChannelPlane(ConcordCommands.entryFor(sc), state, channelId)
                ?: return null to Output.error("no_channel_key", "channel '$channelRef' is not folded, or is private and this account holds no key for it (CORD-03 §1)")
        val relays = ConcordCommands.relaysFor(ctx, sc)
        ctx.registerConcordStreamKeys(relays, listOf(plane.key.secretKey))
        val wraps = ctx.drain(relays.associateWith { listOf(ConcordActions.planeFilter(plane.key.publicKeyHex)) }, pendingOnAuthRequired = true).map { it.second }
        val rumors = ConcordActions.channelRumors(wraps, plane.key, channelId, plane.epoch).distinctBy { it.id }
        return OpenChannel(state, channelId, plane, relays, rumors) to 0
    }

    private suspend fun publish(
        ctx: Context,
        channel: OpenChannel,
        wrap: Event,
        fields: Map<String, Any?>,
    ): Int {
        val ack = ctx.publish(wrap, channel.relays)
        RawEventSupport.publishGuard(ack, wrap.id)?.let { return it }
        // The rumor's id is what readers, deletes and hides name; the wrap's is only the relay's.
        val rumorId = ConcordActions.openChannelRumorAnyExpiry(wrap, channel.plane.key, channel.channelId, channel.plane.epoch)?.id
        Output.emit(mapOf("event_id" to wrap.id, "rumor_id" to rumorId, "channel" to channel.channelId) + fields + RawEventSupport.ackFields(ack))
        return 0
    }

    private fun ids(raw: String): List<String>? =
        raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { list -> list.isNotEmpty() && list.all { HEX64.matches(it) } }

    /** `amy concord edit COMMUNITY CHANNEL RUMOR_ID TEXT`: replaces the text of one of our own messages. */
    suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val targetId = args.positional(2, "rumor-id")
        val text = args.positional(3, "text")
        args.rejectUnknown()
        if (!HEX64.matches(targetId)) return Output.error("bad_args", "expected a message's 64-hex rumor id, got '$targetId'")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (channel, code) = open(ctx, dataDir, handle, channelRef)
            channel ?: return code
            val target = channel.rumor(targetId) ?: return Output.error("not_found", "no message $targetId on this channel's current plane")
            if (target.kind != ChatEvent.KIND) return Output.error("not_editable", "only a channel message (kind 9) can be edited; $targetId is a kind ${target.kind}")
            // Every reader applies only the author's own edits, so anyone else's would vanish unseen.
            if (!target.pubKey.equals(ctx.signer.pubKey, ignoreCase = true)) return Output.error("not_yours", "message $targetId was written by ${target.pubKey}; only its author's edits apply")
            val wrap = ConcordActions.buildChannelEdit(ctx.signer, channel.plane.key, channel.channelId, channel.plane.epoch, target, text, TimeUtils.now())
            return publish(ctx, channel, wrap, mapOf("edited" to target.id))
        }
    }

    /** `amy concord delete COMMUNITY CHANNEL RUMOR_ID[,RUMOR_ID…]`: retracts our own messages or reactions. */
    suspend fun delete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val raw = args.positional(2, "rumor-id[,rumor-id…]")
        args.rejectUnknown()
        val wanted = ids(raw) ?: return Output.error("bad_args", "expected comma-separated 64-hex rumor ids, got '$raw'")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (channel, code) = open(ctx, dataDir, handle, channelRef)
            channel ?: return code
            val targets = wanted.map { id -> channel.rumor(id) ?: return Output.error("not_found", "no rumor $id on this channel's current plane") }
            // A reader honors a member's delete only for what they wrote; a moderator hides with `hide`.
            targets.firstOrNull { !it.pubKey.equals(ctx.signer.pubKey, ignoreCase = true) }?.let {
                return Output.error("not_yours", "rumor ${it.id} was written by ${it.pubKey}; delete only retracts your own (a moderator uses `amy concord hide`)")
            }
            val wrap = ConcordActions.buildChannelDelete(ctx.signer, channel.plane.key, channel.channelId, channel.plane.epoch, targets, TimeUtils.now())
            return publish(ctx, channel, wrap, mapOf("deleted" to targets.map { it.id }))
        }
    }

    /** `amy concord react COMMUNITY CHANNEL RUMOR_ID [REACTION]`: a reaction, `+` by default. */
    suspend fun react(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val targetId = args.positional(2, "rumor-id")
        val reaction = args.positionalOrNull(3) ?: "+"
        args.rejectUnknown()
        if (!HEX64.matches(targetId)) return Output.error("bad_args", "expected a message's 64-hex rumor id, got '$targetId'")
        if (reaction.isBlank()) return Output.error("bad_args", "a reaction can't be blank")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (channel, code) = open(ctx, dataDir, handle, channelRef)
            channel ?: return code
            val target = channel.rumor(targetId) ?: return Output.error("not_found", "no message $targetId on this channel's current plane")
            if (target.kind == ReactionEvent.KIND) return Output.error("bad_args", "$targetId is itself a reaction")
            val wrap =
                ConcordActions.buildChannelReaction(
                    ctx.signer,
                    channel.plane.key,
                    channel.channelId,
                    channel.plane.epoch,
                    target,
                    reaction,
                    TimeUtils.now(),
                    timerSecs = channel.state.metadata?.messageExpirationSecs(),
                )
            return publish(ctx, channel, wrap, mapOf("reacted_to" to target.id, "reaction" to reaction))
        }
    }

    /**
     * `amy concord hide COMMUNITY CHANNEL RUMOR_ID[,RUMOR_ID…]`: a moderator's hide of other members'
     * messages (CORD-03 §3), refused for any target the fold would not honor it over.
     */
    suspend fun hide(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val handle = args.positional(0, "community")
        val channelRef = args.positional(1, "channel")
        val raw = args.positional(2, "rumor-id[,rumor-id…]")
        args.rejectUnknown()
        val wanted = ids(raw) ?: return Output.error("bad_args", "expected comma-separated 64-hex rumor ids, got '$raw'")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val (channel, code) = open(ctx, dataDir, handle, channelRef)
            channel ?: return code
            val me = ctx.signer.pubKey
            val citation = AuthorityCitations.forActor(channel.state.authority, me)
            val targets = wanted.map { id -> channel.rumor(id) ?: return Output.error("not_found", "no rumor $id on this channel's current plane") }
            targets.firstOrNull { !channel.state.honorsModerationHide(me, it.pubKey, channel.channelId, citation) }?.let {
                return Output.error(
                    "not_allowed",
                    "readers would not honor a hide of ${it.id} by ${it.pubKey}: a hide needs MANAGE_MESSAGES in the channel and a rank strictly above the author's (CORD-03 §3); your own messages go with `amy concord delete`",
                )
            }
            val wrap = ConcordActions.buildChannelModerationHide(ctx.signer, channel.plane.key, channel.channelId, channel.plane.epoch, targets, citation, TimeUtils.now())
            return publish(ctx, channel, wrap, mapOf("hidden" to targets.map { it.id }, "cited" to (citation != null)))
        }
    }
}
