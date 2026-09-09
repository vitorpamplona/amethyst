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
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
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
        val legacy = args.bool("legacy")
        args.rejectUnknown()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = RandomInstance.bytes(32).toHexKey()
            val outboxUrls = ctx.outboxRelays().map { it.url }

            if (legacy) {
                // MIP-era group: its GroupContext requires the `0xF2EE`
                // group-data extension, so only members whose leaves advertise
                // that capability can be added. Kept for reproducing the
                // behaviour of groups already on disk.
                //
                // Bake MarmotGroupData into the epoch-0 GroupContext directly
                // so later invitees receive a pre-populated group from the
                // welcome and never have to chase an undecryptable bootstrap
                // commit that predates their membership.
                val metadata =
                    MarmotGroupData.bootstrap(
                        nostrGroupId = gid,
                        creatorPubKey = ctx.identity.pubKeyHex,
                        outboxRelays = outboxUrls,
                        name = name,
                    )
                ctx.marmot.createGroup(gid, initialMetadata = metadata)
            } else {
                // Current profile by default. The difference is what the group
                // REQUIRES of a joining leaf: a current-profile group asks for
                // the account identity proof, which every conformant peer's
                // KeyPackage carries, while a legacy group asks for `0xF2EE`,
                // which none of them do. Defaulting to legacy made every
                // outside member un-addable.
                ctx.marmot.createCurrentProfileGroup(
                    nostrGroupId = gid,
                    relays = outboxUrls,
                    profile = if (name.isEmpty()) null else GroupProfileV1(name, ""),
                )
            }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "mls_group_id" to ctx.marmot.mlsGroupIdHex(gid),
                    "name" to name,
                    "profile" to if (legacy) "legacy" else "current",
                    "epoch" to ctx.marmot.groupEpoch(gid),
                ),
            )
            return 0
        }
    }
}
