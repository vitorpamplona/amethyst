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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent

/**
 * Read verbs for relay groups: the user's joined list (kind 10009), a relay's
 * whole hosted directory, and a single group's metadata + roster. All reads drain
 * a one-shot subscription and parse with Quartz's NIP-29 events.
 */
object RelayGroupReadCommands {
    /** `relaygroup list` — the caller's joined groups from their kind-10009 list (public + private). */
    suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutSecs = args.longFlag("timeout", 8L)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val relays = ctx.outboxRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no relays configured; run `amy relay add`")

            val filter = Filter(kinds = listOf(SimpleGroupListEvent.KIND), authors = listOf(ctx.identity.pubKeyHex), limit = 1)
            val latest =
                ctx
                    .drain(relays.associateWith { listOf(filter) }, timeoutSecs * 1000)
                    .map { it.second }
                    .filterIsInstance<SimpleGroupListEvent>()
                    .maxByOrNull { it.createdAt }

            val groups =
                latest?.let { event ->
                    val pub = event.publicGroups()
                    val priv = event.privateGroups(ctx.signer) ?: emptyList()
                    (pub + priv).distinctBy { it.groupId to it.relayUrl }
                } ?: emptyList()

            Output.emit(
                mapOf(
                    "count" to groups.size,
                    "groups" to
                        groups.map {
                            mapOf("group_id" to it.groupId, "relay" to it.relayUrl, "name" to it.name)
                        },
                ),
            )
            return 0
        }
    }

    /** `relaygroup browse RELAY [--timeout S]` — every group the relay hosts (its 39000 directory). */
    suspend fun browse(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", "relaygroup browse RELAY")
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutSecs = args.longFlag("timeout", 8L)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val filter = Filter(kinds = listOf(GroupMetadataEvent.KIND), limit = 500)
            val metas =
                ctx
                    .drain(mapOf(relay to listOf(filter)), timeoutSecs * 1000)
                    .map { it.second }
                    .filterIsInstance<GroupMetadataEvent>()
                    .distinctBy { it.groupId() }
                    .sortedBy { it.name()?.lowercase() ?: it.groupId() }

            Output.emit(
                mapOf(
                    "relay" to relay.url,
                    "count" to metas.size,
                    "groups" to metas.map(::metaSummary),
                ),
            )
            return 0
        }
    }

    /** `relaygroup info RELAY GROUP_ID [--timeout S]` — one group's metadata + admin/member roster. */
    suspend fun info(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", "relaygroup info RELAY GROUP_ID")
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", "relaygroup info RELAY GROUP_ID")
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutSecs = args.longFlag("timeout", 8L)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val filter =
                Filter(
                    kinds = listOf(GroupMetadataEvent.KIND, GroupAdminsEvent.KIND, GroupMembersEvent.KIND),
                    tags = mapOf("d" to listOf(groupId)),
                    limit = 10,
                )
            val events = ctx.drain(mapOf(relay to listOf(filter)), timeoutSecs * 1000).map { it.second }

            val meta = events.filterIsInstance<GroupMetadataEvent>().maxByOrNull { it.createdAt }
            val admins = events.filterIsInstance<GroupAdminsEvent>().maxByOrNull { it.createdAt }?.admins() ?: emptyList()
            val members = events.filterIsInstance<GroupMembersEvent>().maxByOrNull { it.createdAt }?.members() ?: emptyList()
            val memberCount = (members + admins.map { it.pubKey }).distinct().size

            if (meta == null && admins.isEmpty() && members.isEmpty()) {
                return Output.error("not_found", "no group $groupId found on ${relay.url}")
            }

            Output.emit(
                mapOf(
                    "group_id" to groupId,
                    "relay" to relay.url,
                    "name" to meta?.name(),
                    "about" to meta?.about(),
                    "picture" to meta?.picture(),
                    "private" to (meta?.isPrivate() ?: false),
                    "closed" to (meta?.isClosed() ?: false),
                    "member_count" to memberCount,
                    "admins" to admins.map { mapOf("pubkey" to it.pubKey, "roles" to it.roles) },
                    "members" to members,
                ),
            )
            return 0
        }
    }

    private fun metaSummary(meta: GroupMetadataEvent) =
        mapOf(
            "group_id" to meta.groupId(),
            "name" to meta.name(),
            "about" to meta.about(),
            "private" to meta.isPrivate(),
            "closed" to meta.isClosed(),
        )
}
