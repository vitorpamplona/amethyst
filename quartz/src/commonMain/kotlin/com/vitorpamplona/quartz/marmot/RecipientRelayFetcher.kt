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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * One-shot lookup of a user's published relay-routing events. Used by stateless
 * callers (the `amy` CLI, automated agents) that don't keep a long-lived
 * `LocalCache` around to subscribe to these events as they arrive.
 *
 * The Amethyst Android app does the equivalent via `cache.getOrCreateUser(pk)
 * .dmInboxRelays()` / `.outboxRelays()` after its background subscriptions
 * have populated the cache. The CLI doesn't have that pipeline, so it has to
 * pull the same three replaceable events on demand whenever it needs to
 * deliver something to someone whose relays it doesn't already know.
 *
 * Specifically required for Marmot (MIP-00..03):
 *  - kind:10050 — where to deliver the NIP-59 Welcome gift wrap.
 *  - kind:10051 — where to read MIP-00 KeyPackages from.
 *  - kind:10002 — fallback for both, plus a generic outbox/inbox advertisement.
 */
object RecipientRelayFetcher {
    data class Lists(
        /** kind:10050 relays — where the user's NIP-17 / NIP-59 inbox lives. */
        val dmInbox: List<NormalizedRelayUrl>,
        /** kind:10051 relays — where the user hosts MIP-00 KeyPackages. */
        val keyPackage: List<NormalizedRelayUrl>,
        /** Latest kind:10002 the user published (null if none seen). */
        val nip65: AdvertisedRelayListEvent?,
    ) {
        /** Read-marker relays from kind:10002. Mirrors `User.inboxRelays()`. */
        fun nip65Read(): List<NormalizedRelayUrl> = nip65?.readRelaysNorm().orEmpty()

        /** Write-marker relays from kind:10002. Mirrors `User.outboxRelays()`. */
        fun nip65Write(): List<NormalizedRelayUrl> = nip65?.writeRelaysNorm().orEmpty()

        /**
         * Where to deliver a NIP-59 gift wrap addressed to this user.
         * Mirrors `User.dmInboxRelays()`: prefer kind:10050, fall back to the
         * NIP-65 read marker.
         */
        fun dmInboxOrFallback(): List<NormalizedRelayUrl> = dmInbox.ifEmpty { nip65Read() }
    }

    /**
     * Drain the latest kind:10050, 10051, 10002 events for [pubKey] from
     * [seedRelays] and return them grouped by kind. Replaceable-event
     * semantics: when a relay returns multiple versions, the newest
     * `created_at` wins.
     *
     * Returns empty lists if no relays were given or no events arrived in
     * time — callers decide how to fall back.
     */
    suspend fun fetchRelayLists(
        client: INostrClient,
        pubKey: HexKey,
        seedRelays: Set<NormalizedRelayUrl>,
        timeoutMs: Long = 8_000L,
    ): Lists {
        if (seedRelays.isEmpty()) return Lists(emptyList(), emptyList(), null)

        val filter =
            Filter(
                kinds =
                    listOf(
                        ChatMessageRelayListEvent.KIND,
                        KeyPackageRelayListEvent.KIND,
                        AdvertisedRelayListEvent.KIND,
                    ),
                authors = listOf(pubKey),
            )

        val events =
            client.fetchAll(
                filters = seedRelays.associateWith { listOf(filter) },
                timeoutMs = timeoutMs,
            )

        var dm: ChatMessageRelayListEvent? = null
        var kp: KeyPackageRelayListEvent? = null
        var nip65: AdvertisedRelayListEvent? = null
        for (event in events) {
            // Authors filter is enforced relay-side, but a malicious or buggy
            // relay could echo something else. Keep the guard so we never
            // route someone else's wrapped welcome to the wrong inbox.
            if (event.pubKey != pubKey) continue
            when (event) {
                is ChatMessageRelayListEvent -> {
                    if (dm == null || event.createdAt > dm.createdAt) dm = event
                }

                is KeyPackageRelayListEvent -> {
                    if (kp == null || event.createdAt > kp.createdAt) kp = event
                }

                is AdvertisedRelayListEvent -> {
                    if (nip65 == null || event.createdAt > nip65.createdAt) nip65 = event
                }
            }
        }

        return Lists(
            dmInbox = dm?.relays().orEmpty(),
            keyPackage = kp?.relays().orEmpty(),
            nip65 = nip65,
        )
    }
}
