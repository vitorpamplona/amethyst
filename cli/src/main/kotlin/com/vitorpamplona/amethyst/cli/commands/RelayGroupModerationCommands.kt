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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.ChannelPinsEvent
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.PinMessageEvent
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.UnpinMessageEvent
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLES
import com.vitorpamplona.quartz.buzz.workspace.BuzzChannelLabels
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.FetchAllResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin

/**
 * Moderator/admin write verbs for a relay group (the relay is the final authority
 * and rejects a request from someone lacking the role). All pinned to the group's
 * host relay via [publishScoped] or a direct publish.
 */
object RelayGroupModerationCommands {
    /**
     * `relaygroup edit RELAY GROUP_ID [--name N] [--about A] [--picture URL] [--banner URL]
     * [--parent GID|--root] [--private|--public] [--closed|--open]` → 9002.
     *
     * A kind-9002 edit re-asserts the whole group metadata ("all the fields of group-metadata"), so
     * sending only one field would silently reset the rest (e.g. `--closed` on a private group would
     * drop `private` and leak it public; a rename would drop the picture, banner, subgroup links and
     * any tag we don't model). To avoid that we read the group's current 39000 metadata and merge:
     * every field keeps its current value unless the caller explicitly changes it, and unknown tags
     * (`livekit`, `supported_kinds`, …) ride along verbatim.
     */
    suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup edit RELAY GROUP_ID [--name N] [--about A] [--picture URL] [--banner URL] [--parent GID|--root] [--private|--public] [--closed|--open]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val filter =
                Filter(kinds = listOf(GroupMetadataEvent.KIND), tags = mapOf("d" to listOf(groupId)), limit = 1)
            val meta =
                ctx
                    .drain(mapOf(relay to listOf(filter)), 6_000)
                    .map { it.second }
                    .filterIsInstance<GroupMetadataEvent>()
                    .maxByOrNull { it.createdAt }
            if (meta == null) {
                System.err.println(
                    "warning: could not read current metadata for $groupId on ${relay.url}; " +
                        "visibility will be set from the flags given only",
                )
            }

            val isPrivate =
                if (args.bool("private")) {
                    true
                } else if (args.bool("public")) {
                    false
                } else {
                    (meta?.isPrivate() ?: false)
                }
            val isClosed =
                if (args.bool("closed")) {
                    true
                } else if (args.bool("open")) {
                    false
                } else {
                    (meta?.isClosed() ?: false)
                }

            // A kind-9002 re-asserts the whole metadata, so omitted fields must be carried over
            // from the current 39000 — otherwise editing only a flag would wipe name/about/tags.
            // Except on Buzz, whose `t` tags are the channel type and labels (echoing them gets the
            // edit refused) and whose relay-internal tags a channel admin may not set.
            val template =
                GroupEditMetadataEvent.buildEdit(
                    groupId,
                    currentMetadata = meta,
                    isBuzz = BuzzChannelLabels.looksLikeBuzzGroupStateOfAnyVersion(meta),
                    name = args.flag("name") ?: meta?.name(),
                    about = args.flag("about") ?: meta?.about(),
                    picture = args.flag("picture") ?: meta?.picture(),
                    banner = args.flag("banner") ?: meta?.banner(),
                    status = groupStatus(isPrivate, isClosed) + carriedStatus(meta),
                    hashtags = meta?.hashtags() ?: emptyList(),
                    geohashes = meta?.geohashes()?.maxByOrNull { it.length }?.let { listOf(it) } ?: emptyList(),
                    // Subgroups: a 9002 without `parent` re-roots the group and one missing a `child` is
                    // rejected, so keep both unless the caller re-parents (--parent) or detaches (--root).
                    parent = if (args.bool("root")) null else args.flag("parent") ?: meta?.parent(),
                    children = meta?.children() ?: emptyList(),
                    buzzLabels = null,
                )
            args.rejectUnknown()
            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "private" to isPrivate,
                    "closed" to isClosed,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /** The status flags the CLI has no switch for (`hidden`, `restricted`), carried from [meta]. */
    private fun carriedStatus(meta: GroupMetadataEvent?): Set<GroupMetadataEvent.GroupStatus> =
        buildSet {
            if (meta?.isHidden() == true) add(GroupMetadataEvent.GroupStatus.HIDDEN)
            if (meta?.isRestricted() == true) add(GroupMetadataEvent.GroupStatus.RESTRICTED)
        }

    /**
     * `relaygroup pin|unpin RELAY GROUP_ID REF` → 9010 update-pin-list. NIP-29 replaces the whole
     * list, so this reads the group's current kind-39005 and re-submits it with REF added (at the
     * end) or removed — every other pin, `e` or `a`, kept verbatim. REF is an event (`note1`,
     * `nevent1`, 64-hex → `e`) or an addressable event (`naddr1`, `kind:pubkey:d` → `a`).
     *
     * The 39005 is only trusted when signed by the relay's NIP-11 `self` key, and a read that did not
     * reach EOSE aborts instead of being taken as "no pins" — publishing a full replacement from a
     * failed read would wipe every existing pin.
     */
    suspend fun pin(
        dataDir: DataDir,
        rest: Array<String>,
        pin: Boolean,
    ): Int {
        val args = Args(rest)
        val verb = if (pin) "pin" else "unpin"
        val usage = "relaygroup $verb RELAY GROUP_ID REF"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val ref = args.positionalOrNull(2) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val target = GroupPin.fromReference(ref) ?: return Output.error("bad_args", "not an event id, nevent, naddr or kind:pubkey:d: $ref")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val info = ctx.relayInfo(relay)
            if (BuzzRelayDialect.isBuzz(relay) || BuzzRelayDialect.looksLikeBuzzRelay(relay, info)) {
                return buzzPin(ctx, relay, info?.self, groupId, target, pin)
            }
            // NIP-29: the 39005 is "signed by the relay keypair … as stated by the NIP-11 `self`".
            // Without that key we cannot tell the real list from a forged one, so do not rewrite it.
            val relayKey =
                info?.self
                    ?: return Output.error("no_relay_key", "could not read the NIP-11 self pubkey of ${relay.url}; refusing to rewrite the pin list")
            val filter =
                Filter(
                    kinds = listOf(GroupPinnedEvent.KIND),
                    authors = listOf(relayKey),
                    tags = mapOf("d" to listOf(groupId)),
                    limit = 1,
                )
            val current =
                when (val read = readPinList(ctx.drainResult(mapOf(relay to listOf(filter)), 6_000), relay, relayKey)) {
                    is PinListRead.Found -> read.pins
                    is PinListRead.Failed -> return Output.error(read.code, read.detail)
                }

            val updated =
                if (pin) {
                    if (current.any { it.ref == target.ref }) current else current + target
                } else {
                    current.filter { it.ref != target.ref }
                }

            val signed = ctx.signer.sign(GroupUpdatePinListEvent.build(groupId, updated))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "pins" to updated.map { it.ref },
                    "changed" to (updated.map { it.ref } != current.map { it.ref }),
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /**
     * Buzz (NIP-PM): one kind-40010 pin or 40011 unpin command naming the message; the relay
     * applies it and republishes the channel's relay-signed 30623, read back here for the output.
     * Buzz refuses NIP-29's 9010 as an unknown kind, and pins messages by id only.
     */
    private suspend fun buzzPin(
        ctx: Context,
        relay: NormalizedRelayUrl,
        relayKey: HexKey?,
        groupId: String,
        target: GroupPin,
        pin: Boolean,
    ): Int {
        val eventId = (target as? EventPin)?.eventId ?: return Output.error("bad_args", "a Buzz channel pins messages by event id (note1/nevent1/hex), not by address: ${target.ref}")
        val template = if (pin) PinMessageEvent.build(groupId, eventId) else UnpinMessageEvent.build(groupId, eventId)
        val signed = ctx.signer.sign(template)
        val ack = ctx.publish(signed, setOf(relay))
        RawEventSupport.publishGuard(ack, signed.id, extra = mapOf("group_id" to groupId, "dialect" to "buzz"))?.let { return it }
        val pins = (readPinList(ctx.drainResult(mapOf(relay to listOf(buzzPinListFilter(groupId, relayKey))), 6_000), relay, relayKey) as? PinListRead.Found)?.pins
        Output.emit(
            mapOf(
                "event_id" to signed.id,
                "kind" to signed.kind,
                "group_id" to groupId,
                "relay" to relay.url,
                "dialect" to "buzz",
                "target" to eventId,
                "pins" to pins?.map { it.ref },
                "pins_verified" to (relayKey != null),
                "published" to ack.values.any { it.accepted },
            ),
        )
        return 0
    }

    /** A Buzz channel's NIP-PM pin list: read by `#h`, which names the channel under NIP-CL (a `#d` read gets no new lists). */
    private fun buzzPinListFilter(
        groupId: String,
        relayKey: HexKey?,
    ) = Filter(
        kinds = listOf(ChannelPinsEvent.KIND),
        authors = relayKey?.let { listOf(it) },
        tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId)),
        limit = 1,
    )

    /**
     * `relaygroup pins RELAY GROUP_ID [--timeout SECS]`: the group's pinned messages, in the relay's
     * display order. NIP-29 reads the relay-signed 39005 by `#d`; Buzz the NIP-PM 30623 by `#h`. Only
     * a list signed by the relay's NIP-11 `self` key counts, when the relay names one.
     */
    suspend fun pins(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup pins RELAY GROUP_ID [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 6) * 1000
        args.rejectUnknown("timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val info = ctx.relayInfo(relay)
            val relayKey = info?.self
            val isBuzz = BuzzRelayDialect.isBuzz(relay) || BuzzRelayDialect.looksLikeBuzzRelay(relay, info)
            val filter =
                if (isBuzz) {
                    buzzPinListFilter(groupId, relayKey)
                } else {
                    Filter(kinds = listOf(GroupPinnedEvent.KIND), authors = relayKey?.let { listOf(it) }, tags = mapOf("d" to listOf(groupId)), limit = 1)
                }
            val pins =
                when (val read = readPinList(ctx.drainResult(mapOf(relay to listOf(filter)), timeoutMs), relay, relayKey)) {
                    is PinListRead.Found -> read.pins
                    is PinListRead.Failed -> return Output.error(read.code, read.detail)
                }
            Output.emit(
                mapOf(
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "dialect" to if (isBuzz) "buzz" else "nip29",
                    "pins" to pins.map { it.ref },
                    "count" to pins.size,
                    // False when the relay names no NIP-11 `self`: the list then came from whoever
                    // signed the newest one, and nothing proves it is the relay's.
                    "verified" to (relayKey != null),
                ),
            )
            return 0
        }
    }

    /** The outcome of reading a group's current kind-39005 before a read-merge-write. */
    internal sealed interface PinListRead {
        class Found(
            val pins: List<GroupPin>,
        ) : PinListRead

        class Failed(
            val code: String,
            val detail: String,
        ) : PinListRead
    }

    /**
     * The latest relay-signed 39005 in [result]; an empty list only when the relay answered (EOSE)
     * and had none. With no [relayKey] any author's list is read, so a caller that reads it must say
     * it is unverified and one that rewrites the list must refuse (see [pin]). A timeout maps to `timeout` (exit 124), any other unanswered read to
     * `fetch_failed` (exit 1).
     */
    internal fun readPinList(
        result: FetchAllResult,
        relay: NormalizedRelayUrl,
        relayKey: HexKey?,
    ): PinListRead {
        val latest =
            result.events
                .map { it.second }
                .filter { it is GroupPinnedEvent || it is ChannelPinsEvent }
                .filter { relayKey == null || it.pubKey == relayKey }
                .maxByOrNull { it.createdAt }
        return when {
            latest is GroupPinnedEvent -> PinListRead.Found(latest.pins())
            latest is ChannelPinsEvent -> PinListRead.Found(latest.pinnedMessageIds().map(::EventPin))
            result.anyRelayServed -> PinListRead.Found(emptyList())
            relay in result.stalled -> PinListRead.Failed("timeout", "${relay.url} did not answer the pin-list read; refusing to overwrite pins")
            else -> PinListRead.Failed("fetch_failed", "could not read the pin list from ${relay.url} (${result.doneReasons[relay] ?: "no answer"}); refusing to overwrite pins")
        }
    }

    /** `relaygroup invite RELAY GROUP_ID --code CODE` → 9009. */
    suspend fun invite(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val code = Args(rest).flag("code") ?: return Output.error("bad_args", "relaygroup invite RELAY GROUP_ID --code CODE")
        return publishScoped(dataDir, rest, "relaygroup invite RELAY GROUP_ID --code CODE", allowedFlags = arrayOf("code")) { _, groupId, _ ->
            GroupCreateInviteEvent.build(groupId, code)
        }
    }

    /**
     * `relaygroup put-user RELAY GROUP_ID PUBKEY [--role admin|moderator] [--buzz-role ROLE]` → 9000.
     *
     * Buzz ignores the roles in the `p` tag and reads only a top-level `role` tag
     * (owner|admin|member|guest|bot); `--buzz-role` sets it. Without it a Buzz relay makes no
     * role change: an existing member keeps their role and a newcomer joins as `member`.
     */
    suspend fun putUser(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup put-user RELAY GROUP_ID PUBKEY [--role admin|moderator] [--buzz-role owner|admin|member|guest|bot]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val user = args.positionalOrNull(2) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val roles =
            args
                .flag("role")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()
        val buzzRole = args.flag("buzz-role")?.trim()?.lowercase()
        if (buzzRole != null && buzzRole !in BUZZ_ROLES) return Output.error("bad_args", usage)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val pubkey = ctx.requireUserHex(user)
            // With --buzz-role the `p` tag stays bare: Buzz reads `p[2]` before the `role` tag and
            // refuses a NIP-29 role (`moderator`) there.
            val signed = ctx.signer.sign(GroupPutUserEvent.buildRoleChange(groupId, pubkey, roles, buzzRole))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "pubkey" to pubkey,
                    "roles" to roles,
                    "buzz_role" to buzzRole,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }

    /** `relaygroup remove-user RELAY GROUP_ID PUBKEY` → 9001. */
    suspend fun removeUser(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "relaygroup remove-user RELAY GROUP_ID PUBKEY"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val user = args.positionalOrNull(2) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val pubkey = ctx.requireUserHex(user)
            val signed = ctx.signer.sign(GroupRemoveUserEvent.build(groupId, listOf(pubkey)))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "pubkey" to pubkey,
                    "published" to ack.values.any { it.accepted },
                ),
            )
            return 0
        }
    }
}
