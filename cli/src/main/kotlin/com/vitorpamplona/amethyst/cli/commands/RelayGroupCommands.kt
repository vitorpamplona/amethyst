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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.JoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.LeaveRequestEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * `amy relaygroup …` — NIP-29 relay-based groups. Every group lives on exactly
 * one host relay and is addressed by (relay, group id); all reads and writes are
 * pinned to that relay. This object owns the lifecycle verbs (create/join/leave/
 * message); reads live in [RelayGroupReadCommands], moderation in
 * [RelayGroupModerationCommands].
 */
object RelayGroupCommands {
    val USAGE: String =
        """
        |Relay groups (NIP-29):
        |  relaygroup list [--timeout S]              joined groups (from kind:10009)
        |  relaygroup browse RELAY [--timeout S]      every group a relay hosts
        |  relaygroup info RELAY GID [--timeout S]    a group's metadata + roster
        |  relaygroup create RELAY --name NAME        create a group (publishes 9007+9002)
        |    [--about A] [--private] [--closed]
        |  relaygroup join RELAY GID [--code CODE]    request to join (kind 9021)
        |    [--reason R]
        |  relaygroup leave RELAY GID                 leave (kind 9022)
        |  relaygroup message RELAY GID TEXT          post a kind-9 chat to the group
        |  relaygroup edit RELAY GID [--name N]       edit metadata (kind 9002, admin);
        |    [--about A] [--private|--public]         reads current visibility and only
        |    [--closed|--open]                        changes the axis you specify
        |  relaygroup invite RELAY GID --code CODE    mint an invite code (kind 9009)
        |  relaygroup put-user RELAY GID PUBKEY       add/promote a user (kind 9000)
        |    [--role admin|moderator]
        |  relaygroup remove-user RELAY GID PUBKEY    kick a user (kind 9001)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "relaygroup",
            tail,
            "relaygroup <list|browse|info|create|join|leave|message|edit|invite|put-user|remove-user> …",
            help = USAGE,
            routes =
                mapOf(
                    "list" to { rest -> RelayGroupReadCommands.list(dataDir, rest) },
                    "browse" to { rest -> RelayGroupReadCommands.browse(dataDir, rest) },
                    "info" to { rest -> RelayGroupReadCommands.info(dataDir, rest) },
                    "create" to { rest -> create(dataDir, rest) },
                    "join" to { rest -> join(dataDir, rest) },
                    "leave" to { rest -> leave(dataDir, rest) },
                    "message" to { rest -> message(dataDir, rest) },
                    "edit" to { rest -> RelayGroupModerationCommands.edit(dataDir, rest) },
                    "invite" to { rest -> RelayGroupModerationCommands.invite(dataDir, rest) },
                    "put-user" to { rest -> RelayGroupModerationCommands.putUser(dataDir, rest) },
                    "remove-user" to { rest -> RelayGroupModerationCommands.removeUser(dataDir, rest) },
                ),
        )

    /** `relaygroup create RELAY --name NAME [--about A] [--private] [--closed]` → publishes 9007 + 9002. */
    private suspend fun create(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", "relaygroup create RELAY --name NAME [--about A] [--private] [--closed]")
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val name = args.flag("name") ?: return Output.error("bad_args", "relaygroup create requires --name")
        val about = args.flag("about")
        val isPrivate = args.bool("private")
        val isClosed = args.bool("closed")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val groupId = RandomInstance.bytes(8).toHexKey()
            val target = setOf(relay)

            val createAck = ctx.publish(ctx.signer.sign(CreateGroupEvent.build(groupId)), target)
            val status = groupStatus(isPrivate, isClosed)
            val edit = EditMetadataEvent.build(groupId, name = name, about = about, status = status)
            val editAck = ctx.publish(ctx.signer.sign(edit), target)
            // Track it in our own kind:10009 so `relaygroup list` shows it, matching
            // the Android create flow (Account.createRelayGroup → follow).
            val listed = updateGroupList(ctx, relay, groupId, add = true)

            Output.emit(
                mapOf(
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "name" to name,
                    "private" to isPrivate,
                    "closed" to isClosed,
                    "published" to (createAck.values.any { it } && editAck.values.any { it }),
                    "listed" to listed,
                ),
            )
            return 0
        }
    }

    /**
     * `relaygroup join RELAY GROUP_ID [--code CODE] [--reason R]` — publishes the
     * 9021 join request to the host relay AND adds the group to the caller's
     * kind-10009 list (private item), so `relaygroup list` reflects it.
     */
    private suspend fun join(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup join RELAY GROUP_ID [--code CODE]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val join = JoinRequestEvent.build(groupId, reason = args.flag("reason") ?: "", inviteCode = args.flag("code"))
            args.rejectUnknown()
            val signed = ctx.signer.sign(join)
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            val listed = updateGroupList(ctx, relay, groupId, add = true)
            Output.emit(
                mapOf("group_id" to groupId, "relay" to relay.url, "published" to ack.values.any { it }, "listed" to listed),
            )
            return 0
        }
    }

    /**
     * `relaygroup leave RELAY GROUP_ID` — publishes the 9022 leave request to the
     * host relay AND removes the group from the caller's kind-10009 list.
     */
    private suspend fun leave(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup leave RELAY GROUP_ID"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(LeaveRequestEvent.build(groupId))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            val listed = updateGroupList(ctx, relay, groupId, add = false)
            Output.emit(
                mapOf("group_id" to groupId, "relay" to relay.url, "published" to ack.values.any { it }, "listed" to listed),
            )
            return 0
        }
    }

    /** `relaygroup message RELAY GROUP_ID <text>` → publishes a kind-9 chat with an `h` tag. */
    private suspend fun message(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val text = args.positionalOrNull(2) ?: return Output.error("bad_args", "relaygroup message RELAY GROUP_ID <text>")
        if (text.isBlank()) return Output.error("bad_args", "message text must not be blank")
        return publishScoped(dataDir, rest, "relaygroup message RELAY GROUP_ID <text>") { _, groupId, _ ->
            ChatEvent.build(text) { hTag(groupId) }
        }
    }
}

/** Normalize a user-supplied relay URL for a group, or null if unparseable. */
internal fun normalizeGroupRelay(url: String): NormalizedRelayUrl? = RelayUrlNormalizer.normalizeOrNull(url.trim())

/**
 * Add or remove a group from the caller's kind-10009 "simple groups" list (private
 * item) and publish the new version to their outbox relays. Mirrors the Android
 * follow/unfollow. Returns true when the updated list was published to ≥1 relay.
 */
private suspend fun updateGroupList(
    ctx: Context,
    relay: NormalizedRelayUrl,
    groupId: String,
    add: Boolean,
): Boolean {
    val outbox = ctx.outboxRelays()
    if (outbox.isEmpty()) return false

    // Load the current list from BOTH the local store (amy's source of truth —
    // every list we've published/synced is here) and a fresh relay drain, then
    // take the newest. Relying on the drain alone is unsafe: a slow or empty
    // fetch would look like "no list", and the `create` branch below would then
    // replace the user's entire kind:10009 with just this one group.
    val filter = Filter(kinds = listOf(SimpleGroupListEvent.KIND), authors = listOf(ctx.identity.pubKeyHex), limit = 1)
    val stored = ctx.latestReplaceable(ctx.identity.pubKeyHex, SimpleGroupListEvent.KIND) as? SimpleGroupListEvent
    val drained =
        ctx
            .drain(outbox.associateWith { listOf(filter) }, 5_000)
            .map { it.second }
            .filterIsInstance<SimpleGroupListEvent>()
            .maxByOrNull { it.createdAt }
    val current = listOfNotNull(stored, drained).maxByOrNull { it.createdAt }

    val tag = GroupTag(groupId, relay.url, null)
    // Public tags, matching the app and the reference NIP-29 clients (membership is
    // already public via the relay's kind-39002 list); reads still merge legacy
    // private items.
    val updated =
        when {
            add && current == null -> SimpleGroupListEvent.create(publicGroups = listOf(tag), signer = ctx.signer)
            add -> SimpleGroupListEvent.add(current!!, tag, isPrivate = false, signer = ctx.signer)
            current == null -> return false // nothing to remove from
            else -> SimpleGroupListEvent.remove(current, tag, signer = ctx.signer)
        }

    return ctx.publish(updated, outbox).values.any { it }
}

/**
 * The NIP-29 status flag set for the given visibility. NIP-29 flags are
 * presence-only: a group is public/open by the ABSENCE of the private/closed
 * tags, so we emit only the restrictive flags that are actually on — never a
 * `["public"]`/`["open"]` tag (which are non-canonical and can confuse relays).
 */
internal fun groupStatus(
    isPrivate: Boolean,
    isClosed: Boolean,
): Set<GroupMetadataEvent.GroupStatus> =
    buildSet {
        if (isPrivate) add(GroupMetadataEvent.GroupStatus.PRIVATE)
        if (isClosed) add(GroupMetadataEvent.GroupStatus.CLOSED)
    }

/**
 * Shared skeleton for the group-scoped write verbs: parse RELAY + GROUP_ID from
 * positionals 0/1, build a template pinned to that group, sign, publish to the
 * host relay, and emit the ack. The [build] lambda returns the event template.
 * [allowedFlags] names flags a caller consumed on a *separate* [Args] instance
 * (e.g. invite's `--code`), so [Args.rejectUnknown] doesn't flag them here.
 */
internal suspend fun publishScoped(
    dataDir: DataDir,
    rest: Array<String>,
    usage: String,
    allowedFlags: Array<String> = emptyArray(),
    build: (relay: NormalizedRelayUrl, groupId: String, args: Args) -> EventTemplate<out Event>,
): Int {
    val args = Args(rest)
    val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
    val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
    val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")

    Context.open(dataDir).use { ctx ->
        ctx.prepare()
        val template = build(relay, groupId, args)
        args.rejectUnknown(*allowedFlags)
        val signed = ctx.signer.sign(template)
        val ack = ctx.publish(signed, setOf(relay))
        RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
        Output.emit(
            mapOf(
                "event_id" to signed.id,
                "kind" to signed.kind,
                "group_id" to groupId,
                "relay" to relay.url,
                "published" to ack.values.any { it },
            ),
        )
        return 0
    }
}
