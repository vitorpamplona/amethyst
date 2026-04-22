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
import com.vitorpamplona.amethyst.cli.Json

/**
 * Read-only queries. None of these publish; they all sync-then-report.
 */
object GroupReadCommands {
    suspend fun list(dataDir: DataDir): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            ctx.syncIncoming()
            val ids = ctx.marmot.activeGroupIds()
            val items =
                ids.map { id ->
                    val m = ctx.marmot.groupMetadata(id)
                    mapOf(
                        "group_id" to id,
                        "name" to (m?.name ?: ""),
                        "members" to ctx.marmot.memberCount(id),
                        "epoch" to ctx.marmot.groupEpoch(id),
                    )
                }
            Json.writeLine(mapOf("groups" to items))
            return 0
        } finally {
            ctx.close()
        }
    }

    suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "group show <group_id>")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)
            val meta = ctx.marmot.groupMetadata(gid)
            val members =
                ctx.marmot.memberPubkeys(gid).map {
                    mapOf("pubkey" to it.pubkey, "leaf_index" to it.leafIndex)
                }
            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "mls_group_id" to ctx.marmot.mlsGroupIdHex(gid),
                    "name" to (meta?.name ?: ""),
                    "description" to (meta?.description ?: ""),
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "admins" to (meta?.adminPubkeys ?: emptyList()),
                    "relays" to (meta?.relays ?: emptyList()),
                    "members" to members,
                    "is_admin" to (meta?.adminPubkeys?.contains(ctx.identity.pubKeyHex) == true),
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    suspend fun members(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "group members <group_id>")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)
            val members =
                ctx.marmot.memberPubkeys(gid).map {
                    mapOf("pubkey" to it.pubkey, "leaf_index" to it.leafIndex)
                }
            Json.writeLine(mapOf("group_id" to gid, "members" to members))
            return 0
        } finally {
            ctx.close()
        }
    }

    suspend fun admins(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "group admins <group_id>")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)
            val m = ctx.marmot.groupMetadata(gid)
            Json.writeLine(mapOf("group_id" to gid, "admins" to (m?.adminPubkeys ?: emptyList())))
            return 0
        } finally {
            ctx.close()
        }
    }
}
