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

object GroupMembershipCommands {
    suspend fun remove(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "group remove <gid> <npub>")
        val gid = rest[0]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val target = ctx.requireUserHex(rest[1])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val leafIndex =
                ctx.marmot.leafIndexOf(gid, target)
                    ?: return Json.error("not_in_group", target)

            val outbound = ctx.marmot.removeMember(nostrGroupId = gid, targetLeafIndex = leafIndex)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(outbound.signedEvent, targets)
            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "removed" to target,
                    "leaf_index" to leafIndex,
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to outbound.signedEvent.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    suspend fun leave(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "group leave <gid>")
        val gid = rest[0]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val outbound = ctx.marmot.leaveGroup(gid)
            val ack = ctx.publish(outbound.signedEvent, targets)
            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "proposal_event_id" to outbound.signedEvent.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
