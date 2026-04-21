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
import com.vitorpamplona.amethyst.cli.util.Npubs
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Metadata-only commits: rename, promote/demote. Each loads current metadata,
 * edits the right field, publishes a GCE commit to the group relays.
 */
object GroupMetadataCommands {
    suspend fun rename(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "group rename <gid> <name>")
        return edit(dataDir, rest[0]) { it.copy(name = rest[1]) }
    }

    suspend fun promote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "group promote <gid> <npub>")
        val newAdmin = Npubs.resolveToHex(rest[1])
        return edit(dataDir, rest[0]) { cur ->
            val admins = cur.adminPubkeys.toMutableList()
            if (newAdmin !in admins) admins.add(newAdmin)
            cur.copy(adminPubkeys = admins)
        }
    }

    suspend fun demote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "group demote <gid> <npub>")
        val target = Npubs.resolveToHex(rest[1])
        return edit(dataDir, rest[0]) { cur ->
            val admins = cur.adminPubkeys.filter { it != target }
            cur.copy(adminPubkeys = admins)
        }
    }

    private suspend fun edit(
        dataDir: DataDir,
        gid: HexKey,
        mutate: (MarmotGroupData) -> MarmotGroupData,
    ): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)
            val cur =
                ctx.marmot.groupMetadata(gid)
                    ?: MarmotGroupData(
                        nostrGroupId = gid,
                        name = "",
                        description = "",
                        adminPubkeys = listOf(ctx.identity.pubKeyHex),
                        relays = ctx.outboxRelays().map { it.url },
                    )
            val updated = mutate(cur)

            val commit = ctx.marmot.updateGroupMetadata(gid, updated)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(commit.signedEvent, targets)

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "name" to updated.name,
                    "admins" to updated.adminPubkeys,
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to commit.signedEvent.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
