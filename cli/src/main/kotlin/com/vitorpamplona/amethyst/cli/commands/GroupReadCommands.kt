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

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output

/**
 * Read-only queries. None of these publish; they all sync-then-report.
 */
object GroupReadCommands {
    suspend fun list(dataDir: DataDir): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            ctx.syncIncoming()
            val ids = ctx.marmot.activeGroupIds()
            val items =
                ids.map { id ->
                    val m = ctx.marmot.groupView(id)
                    mapOf(
                        "group_id" to id,
                        "name" to (m?.name ?: ""),
                        "members" to ctx.marmot.memberCount(id),
                        "epoch" to ctx.marmot.groupEpoch(id),
                    )
                }
            Output.emit(mapOf("groups" to items))
            return 0
        }
    }

    suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group show <group_id>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", gid)
            val meta = ctx.marmot.groupView(gid)
            val members =
                ctx.marmot.memberPubkeys(gid).map {
                    mapOf("pubkey" to it.pubkey, "leaf_index" to it.leafIndex)
                }
            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "mls_group_id" to ctx.marmot.mlsGroupIdHex(gid),
                    "name" to (meta?.name ?: ""),
                    "description" to (meta?.description ?: ""),
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "admins" to (meta?.adminPubkeys ?: emptyList()),
                    "relays" to (meta?.relays ?: emptyList()),
                    "avatar_url" to meta?.avatarUrl?.url,
                    // Hints are opaque bytes by contract; render them as text
                    // only for the conventional UTF-8 case an operator can read.
                    "avatar_dim" to
                        meta
                            ?.avatarUrl
                            ?.dim
                            ?.takeIf { it.isNotEmpty() }
                            ?.decodeToString(),
                    "avatar_thumbhash" to
                        meta
                            ?.avatarUrl
                            ?.thumbhash
                            ?.takeIf { it.isNotEmpty() }
                            ?.decodeToString(),
                    "members" to members,
                    "is_admin" to (meta?.adminPubkeys?.contains(ctx.identity.pubKeyHex) == true),
                    // Disappearing messages, in seconds; 0 means off.
                    "disappearing_secs" to ctx.marmot.retentionSeconds(gid),
                    // The terminal state has no way back, so it is worth
                    // saying out loud rather than leaving a caller to infer it
                    // from a group that quietly refuses every verb.
                    "disbanded" to (ctx.marmot.groupState(gid)?.isDisbanded == true),
                    "lifecycle" to ctx.marmot.lifecycle(gid).name,
                ),
            )
            return 0
        }
    }

    suspend fun members(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group members <group_id>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", gid)
            val members =
                ctx.marmot.memberPubkeys(gid).map {
                    mapOf("pubkey" to it.pubkey, "leaf_index" to it.leafIndex)
                }
            Output.emit(mapOf("group_id" to gid, "members" to members))
            return 0
        }
    }

    suspend fun admins(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group admins <group_id>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", gid)
            val m = ctx.marmot.groupView(gid)
            Output.emit(mapOf("group_id" to gid, "admins" to (m?.adminPubkeys ?: emptyList())))
            return 0
        }
    }
}
