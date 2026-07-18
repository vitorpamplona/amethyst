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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.MarmotFilters
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * The shared "bring local Marmot state current" policy: which filters to ask
 * relays for, how the persisted `since` cursors advance, and when a consumed
 * KeyPackage must be replaced (MIP-00). Extracted from the CLI's
 * `Context.syncIncoming` so every front end (CLI, Android, Desktop) applies the
 * exact same rules — divergence here silently drops DMs/Welcomes.
 *
 * All platform capabilities are injected: the relay topology ([Relays]), the
 * cursor store ([Cursors]), the one-shot subscription drain, and the
 * publish-and-confirm action. The policy itself has zero platform dependencies.
 */
class MarmotSyncPolicy(
    private val marmot: MarmotManager,
    /** The account pubkey the gift-wrap `#p` filter targets. */
    private val userPubKey: HexKey,
    private val cursors: Cursors,
    private val relays: Relays,
    /**
     * One-shot catch-up subscription: subscribe the given per-relay filters,
     * collect events until EOSE-or-timeout, return them tagged by relay.
     * (The CLI passes `Context.drain`; verification/persistence of raw events
     * happens inside the caller's drain, not here.)
     */
    private val drain: suspend (filters: Map<NormalizedRelayUrl, List<Filter>>, timeoutMs: Long) -> List<Pair<NormalizedRelayUrl, Event>>,
    /** Publish an event to the given relays (used for KeyPackage rotation). */
    private val publish: suspend (event: Event, relayList: Set<NormalizedRelayUrl>) -> Unit,
    /** Progress/diagnostic sink; callers prefix/route as they see fit. */
    private val log: (String) -> Unit = {},
) {
    /**
     * Persisted `since` cursors. Backed by `RunState` (`state.json`) in the
     * CLI; any durable per-account key-value store works.
     */
    interface Cursors {
        var giftWrapSince: Long?

        fun groupSince(groupId: HexKey): Long?

        fun setGroupSince(
            groupId: HexKey,
            value: Long,
        )
    }

    /** The account's relay buckets, resolved by the platform. */
    interface Relays {
        /** NIP-17 kind:10050 DM inbox relays — where gift wraps for us land. */
        suspend fun inboxRelays(): Set<NormalizedRelayUrl>

        /** NIP-65 write relays. */
        suspend fun outboxRelays(): Set<NormalizedRelayUrl>

        /** MIP-00 kind:10051 KeyPackage relays. */
        suspend fun keyPackageRelays(): Set<NormalizedRelayUrl>

        /** Union of every configured bucket — the last-resort fallback. */
        suspend fun anyRelays(): Set<NormalizedRelayUrl>

        /** The group's own relay set from its MIP-01 metadata (may be empty). */
        fun groupRelays(nostrGroupId: HexKey): Set<NormalizedRelayUrl>
    }

    /**
     * Pull down everything needed to bring local Marmot state current:
     *  - kind:1059 gift wraps on inbox relays → try to unwrap Welcomes
     *  - kind:445 group events per active group → feed into inbound processor
     *
     * Incrementally advances the `since` cursors in [cursors] so the next run
     * only asks relays for newer events. Two wrinkles:
     *
     *  1. NIP-59 gift wraps are published with a random-past `created_at`
     *     (see [com.vitorpamplona.quartz.utils.TimeUtils.randomWithTwoDays])
     *     so a newly-published wrap can trivially have `created_at` earlier
     *     than the last cursor we saw. To avoid silently dropping such wraps
     *     we always subtract a 2-day lookback window from the gift-wrap
     *     `since`, and dedup is handled inside [MarmotInboundProcessor].
     *  2. We only advance the on-disk cursor when events actually arrive.
     *     Snapping an empty sync up to "now" on the first invocation would
     *     make every later `since` query skip any past-dated wrap or 445.
     */
    suspend fun syncIncoming(timeoutMs: Long = 8_000) {
        val inbox = relays.inboxRelays().ifEmpty { relays.anyRelays() }
        val gwSince = cursors.giftWrapSince
        val gwFilterSince =
            gwSince?.let { (it - GIFT_WRAP_LOOKBACK_SECS).coerceAtLeast(0L) }
        val gwFilter =
            if (gwFilterSince != null) {
                MarmotFilters.giftWrapsForUserSince(userPubKey, gwFilterSince)
            } else {
                MarmotFilters.giftWrapsForUser(userPubKey)
            }

        val activeGroupIds = marmot.subscriptionManager.activeGroupIdsSnapshot().toList()
        val perGroupFilters: Map<HexKey, Filter> =
            activeGroupIds.associateWith { gid ->
                val since = cursors.groupSince(gid)
                if (since != null) {
                    MarmotFilters.groupEventsByGroupIdSince(gid, since)
                } else {
                    MarmotFilters.groupEventsByGroupId(gid)
                }
            }

        // Group filters go to each group's configured relays, not the user's
        // inbox — kind:445 is delivered to the group's relay set advertised in
        // its MIP-01 metadata (falls back to our outbox if the group never
        // stamped any).
        val filterMap = mutableMapOf<NormalizedRelayUrl, MutableList<Filter>>()
        for (r in inbox) filterMap.getOrPut(r) { mutableListOf() }.add(gwFilter)
        for ((gid, filter) in perGroupFilters) {
            val groupRelays = relays.groupRelays(gid).ifEmpty { relays.outboxRelays() }
            for (r in groupRelays) filterMap.getOrPut(r) { mutableListOf() }.add(filter)
        }
        if (filterMap.isEmpty()) return

        val events = drain(filterMap, timeoutMs)

        var maxGwSeen = gwSince ?: 0L
        val maxGroupSeen = perGroupFilters.keys.associateWith { cursors.groupSince(it) ?: 0L }.toMutableMap()
        var sawGiftWrap = false
        val sawGroupEvent = mutableSetOf<HexKey>()

        for ((relay, event) in events) {
            // All the MLS/NIP-59 decryption + persistence lives in MarmotIngest —
            // we only care about bookkeeping (since-cursors, logging) here.
            val result = marmot.ingest(event)
            val detail =
                when (result) {
                    is MarmotIngestResult.Failure -> " ${result.message}"
                    else -> ""
                }
            log("ingest ${event.kind}/${event.id.take(8)} via $relay → ${result::class.simpleName}$detail")

            when (event.kind) {
                GiftWrapEvent.KIND -> {
                    sawGiftWrap = true
                    if (event.createdAt > maxGwSeen) maxGwSeen = event.createdAt
                }

                GroupEvent.KIND -> {
                    val gid = (event as? GroupEvent)?.groupId() ?: continue
                    sawGroupEvent.add(gid)
                    val prev = maxGroupSeen[gid] ?: 0L
                    if (event.createdAt > prev) maxGroupSeen[gid] = event.createdAt
                }
            }
        }

        if (sawGiftWrap && maxGwSeen > 0) {
            cursors.giftWrapSince = maxGwSeen
        }
        for (gid in sawGroupEvent) {
            val seen = maxGroupSeen[gid] ?: continue
            if (seen > 0) cursors.setGroupSince(gid, seen)
        }

        // If any welcome we processed consumed a KeyPackage, MIP-00 requires
        // us to immediately publish a replacement (a KP can only be used for
        // ONE welcome; leaving the old one on relays lets a second sender
        // invite us with a bundle we no longer have private keys for).
        // Callers with their own rotation scheduler (the Amethyst UI) can
        // also rotate elsewhere; callers without one (the CLI) rely on this
        // inline rotation right after sync.
        if (marmot.needsKeyPackageRotation()) {
            try {
                val kpRelays = relays.keyPackageRelays().ifEmpty { relays.outboxRelays() }.ifEmpty { relays.anyRelays() }
                if (kpRelays.isNotEmpty()) {
                    val rotated = marmot.rotateConsumedKeyPackages(kpRelays.toList())
                    for (event in rotated) {
                        publish(event, kpRelays)
                        log("rotated KeyPackage → ${event.id.take(8)} on ${kpRelays.size} relay(s)")
                    }
                }
            } catch (e: Exception) {
                log("key-package rotation failed: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * Lookback applied to the gift-wrap `since` filter to compensate for
         * NIP-59's randomised-past `created_at`. 2 days matches
         * [com.vitorpamplona.quartz.utils.TimeUtils.randomWithTwoDays].
         */
        const val GIFT_WRAP_LOOKBACK_SECS: Long = 2L * 24 * 60 * 60
    }
}
