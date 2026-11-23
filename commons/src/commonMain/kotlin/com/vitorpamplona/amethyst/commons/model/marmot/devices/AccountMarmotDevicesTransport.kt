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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/** [MarmotDevicesTransport] over an [Account]'s relay client and relay lists. */
class AccountMarmotDevicesTransport(
    private val account: Account,
) : MarmotDevicesTransport {
    private fun ownInbox(): Set<NormalizedRelayUrl> =
        account.dmRelayList.flow.value
            .ifEmpty { account.dmRelays.flow.value }
            .ifEmpty { account.outboxRelays.flow.value }

    private fun keyPackageRelays(): Set<NormalizedRelayUrl> = account.marmot.keyPackagePublishRelays().ifEmpty { account.outboxRelays.flow.value }

    override suspend fun publishGroupEvent(event: OutboundGroupEvent): Boolean {
        val relays = account.marmot.marmotGroupRelays(event.nostrGroupId)
        if (relays.isEmpty()) return false
        return account.client.publishAndConfirm(event.signedEvent, relays, untilFirstAccept = true)
    }

    override suspend fun publishToSelf(wrap: GiftWrapEvent): Boolean = account.client.publishAndConfirm(wrap, ownInbox(), untilFirstAccept = true)

    override suspend fun publishWelcome(delivery: WelcomeDelivery): Boolean {
        val relays =
            account.computeRelayListToBroadcast(delivery.giftWrapEvent) +
                account.outboxRelays.flow.value +
                account.cache
                    .getOrCreateUser(delivery.recipientPubKey)
                    .dmInboxRelays()
                    .orEmpty()
        return account.client.publishAndConfirm(delivery.giftWrapEvent, relays, untilFirstAccept = true)
    }

    override suspend fun publishKeyPackage(event: KeyPackageEvent): Boolean {
        account.cache.justConsumeMyOwnEvent(event)
        return account.client.publishAndConfirm(event, keyPackageRelays())
    }

    override suspend fun fetchOwnKeyPackage(eventId: HexKey): KeyPackageEvent? {
        (account.cache.getNoteIfExists(eventId)?.event as? KeyPackageEvent)?.let { return it }
        val relays = keyPackageRelays() + account.outboxRelays.flow.value
        val filter = Filter(ids = listOf(eventId), authors = listOf(account.signer.pubKey), kinds = listOf(KeyPackageEvent.KIND))
        return account.client.fetchFirst(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = FETCH_IDLE_MS) as? KeyPackageEvent
    }

    override suspend fun ownKeyPackages(): List<KeyPackageEvent>? {
        val relays = keyPackageRelays()
        if (relays.isEmpty()) return null
        val filter = Filter(authors = listOf(account.signer.pubKey), kinds = listOf(KeyPackageEvent.KIND))
        val result = account.client.fetchAllWithHooks(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = FETCH_IDLE_MS) { _, _ -> true }
        // Nobody answered: unknown, not "none" (an empty list would be recorded as the first check).
        if (!result.anyRelayServed) return null
        return result.events
            .mapNotNull { it.second as? KeyPackageEvent }
            .groupBy { it.dTag() }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
    }

    override suspend fun publishOwnEvent(event: Event): Boolean = account.client.publishAndConfirm(event, account.outboxRelays.flow.value + keyPackageRelays())

    companion object {
        const val FETCH_IDLE_MS = 8_000L
    }
}
