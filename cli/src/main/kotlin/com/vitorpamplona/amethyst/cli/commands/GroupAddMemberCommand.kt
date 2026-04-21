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
 * `group add <group_id> <npub> [<npub> ...]` — fetch each invitee's
 * KeyPackage from the union of (our relays + any known KeyPackage relays
 * for them) and run the full add-member flow for each one: build commit,
 * publish commit to the group's relays, then wrap + publish the Welcome
 * gift wrap.
 */
object GroupAddMemberCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "group add <group_id> <npub> [<npub> ...]")
        val gid = rest[0]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            // Accept any identifier the UI would: npub1…, nprofile1…, 64-hex,
            // NIP-05 (name@domain). Resolution fires NIP-05 HTTP fetches in parallel
            // where applicable; bech32/hex stays fully offline.
            val invitees = rest.drop(1).map { ctx.requireUserHex(it) }

            val groupRelays = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val report = mutableListOf<Map<String, Any?>>()

            for (pub in invitees) {
                val relays =
                    com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                        .fetchRelaysFor(emptySet(), emptySet(), ctx.anyRelays())
                val kpEvent =
                    com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                        .fetchKeyPackage(ctx.client, pub, relays, timeoutMs = 10_000)
                if (kpEvent == null) {
                    report.add(mapOf("pubkey" to pub, "status" to "no_key_package"))
                    continue
                }

                val (commitEvent, welcomeDelivery) =
                    ctx.marmot.addMember(
                        nostrGroupId = gid,
                        keyPackageEvent = kpEvent,
                        relays = groupRelays.toList(),
                    )

                // Order matters: commit first (so invitee doesn't join at a future epoch),
                // then welcome.
                val commitAck = ctx.publish(commitEvent.signedEvent, groupRelays)
                val welcomeAck =
                    if (welcomeDelivery != null) {
                        val inbox = ctx.inboxRelays().ifEmpty { ctx.outboxRelays() }
                        ctx.publish(welcomeDelivery.giftWrapEvent, inbox)
                    } else {
                        emptyMap()
                    }

                report.add(
                    mapOf(
                        "pubkey" to pub,
                        "status" to "invited",
                        "key_package_event_id" to kpEvent.id,
                        "commit_event_id" to commitEvent.signedEvent.id,
                        "welcome_event_id" to welcomeDelivery?.giftWrapEvent?.id,
                        "commit_accepted_by" to commitAck.filterValues { it }.keys.map { it.url },
                        "welcome_accepted_by" to welcomeAck.filterValues { it }.keys.map { it.url },
                    ),
                )
            }

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "results" to report,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
