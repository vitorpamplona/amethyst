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
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance

object GroupCreateCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val name = args.flag("name", "")!!
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = RandomInstance.bytes(32).toHexKey()

            ctx.marmot.createGroup(gid)

            // Stamp initial metadata: name + our outbox relays + self as admin.
            // Mirrors CreateGroupScreen.proceedWithCreate() on the Amethyst side.
            val outboxUrls = ctx.outboxRelays().map { it.url }
            val metadata =
                MarmotGroupData(
                    nostrGroupId = gid,
                    name = name,
                    description = "",
                    adminPubkeys = listOf(ctx.identity.pubKeyHex),
                    relays = outboxUrls,
                )
            val commit = ctx.marmot.updateGroupMetadata(gid, metadata)

            // Group relays == what the metadata carries, which on first commit is our outbox.
            val targets = ctx.outboxRelays()
            val ack = ctx.publish(commit.signedEvent, targets)

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "name" to name,
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
