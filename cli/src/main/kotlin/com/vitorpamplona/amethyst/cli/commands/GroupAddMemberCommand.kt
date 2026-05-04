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
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * `group add <group_id> <npub> [<npub> ...]` — fetch each invitee's
 * KeyPackage from the union of (their advertised KeyPackage relays +
 * their NIP-65 outbox + our bootstrap relays) and run the full add-member
 * flow for each one: build commit, publish commit to the group's relays,
 * then wrap + publish the Welcome gift wrap to the invitee's DM inbox
 * (kind:10050) with their NIP-65 read relays as a fallback.
 *
 * Two different users could have completely disjoint relay configurations
 * and still successfully marmot each other — we discover where each
 * invitee is actually listening before routing anything to them.
 */
object GroupAddMemberCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group add <group_id> <npub> [<npub> ...]")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", gid)

            // Accept any identifier the UI would: npub1…, nprofile1…, 64-hex,
            // NIP-05 (name@domain). Resolution fires NIP-05 HTTP fetches in parallel
            // where applicable; bech32/hex stays fully offline.
            val invitees = rest.drop(1).map { ctx.requireUserHex(it) }

            val groupRelays = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            // Computed once: the seed relays we query for any stranger's
            // published relay-routing events. Union of our own configured
            // relays and Amethyst's hard-coded defaults so we stay useful
            // when an invitee shares nothing with us but used Amethyst to
            // bootstrap.
            val seed = ctx.bootstrapRelays()
            val report = mutableListOf<Map<String, Any?>>()

            for (pub in invitees) {
                // Discover where *this* invitee actually reads from. Without
                // this the inviter can only broadcast to their own relays,
                // which silently fails the moment the two users have
                // disjoint relay configs.
                //
                // Cache-first via Context.cachedRelayListsOf — every
                // relay list seen previously is in the local store
                // already.
                val recipient =
                    ctx.cachedRelayListsOf(pub)
                        ?: RecipientRelayFetcher.fetchRelayLists(
                            client = ctx.client,
                            pubKey = pub,
                            seedRelays = seed,
                        )

                // KeyPackage discovery (MIP-00): prefer the invitee's own
                // kind:10051, then their kind:10002 write marker, then our
                // bootstrap pool as a last-resort fallback.
                val kpRelays =
                    KeyPackageFetcher.fetchRelaysFor(
                        targetKeyPackageRelays = recipient.keyPackage,
                        targetOutbox = recipient.nip65Write(),
                        myOutbox = seed,
                    )
                val kpEvent =
                    KeyPackageFetcher.fetchKeyPackage(
                        client = ctx.client,
                        targetPubKey = pub,
                        relays = kpRelays,
                        timeoutMs = 10_000,
                    )
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
                val welcomeTargets: Set<NormalizedRelayUrl> =
                    if (welcomeDelivery != null) {
                        // Welcome gift wrap (kind:1059 wrapping kind:444) must
                        // land on a relay the invitee actually polls for their
                        // NIP-59 inbox. Priority:
                        //   1. kind:10050 (their explicit DM inbox)
                        //   2. kind:10002 read markers (NIP-65 fallback,
                        //      matches User.dmInboxRelays())
                        //   3. Amethyst's DefaultDMRelayList (best-effort if
                        //      the invitee has published nothing — freshly-
                        //      bootstrapped Amethyst accounts listen on these)
                        // Our own outbox is added as belt-and-braces so we
                        // can re-ingest the welcome ourselves too.
                        buildSet {
                            addAll(recipient.dmInboxOrFallback())
                            if (isEmpty()) {
                                addAll(DefaultDMRelayList)
                            }
                            addAll(ctx.outboxRelays())
                        }
                    } else {
                        emptySet()
                    }
                val welcomeAck =
                    if (welcomeDelivery != null && welcomeTargets.isNotEmpty()) {
                        ctx.publish(welcomeDelivery.giftWrapEvent, welcomeTargets)
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
                        "welcome_targets" to welcomeTargets.map { it.url },
                        "key_package_relays" to kpRelays.map { it.url },
                    ),
                )
            }

            Output.emit(
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
