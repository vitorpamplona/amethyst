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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesTransport
import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/** amy's [MarmotDevicesTransport]: [Context.publish] and [Context.drain] over the account's relay lists. */
class CliMarmotDevicesTransport(
    private val ctx: Context,
) : MarmotDevicesTransport {
    private fun accepted(results: Map<*, PublishResult>) = results.values.any { it.accepted }

    override suspend fun publishGroupEvent(event: OutboundGroupEvent): Boolean {
        val relays = ctx.marmotGroupRelays(event.nostrGroupId)
        return relays.isNotEmpty() && accepted(ctx.publish(event.signedEvent, relays))
    }

    override suspend fun publishToSelf(wrap: GiftWrapEvent): Boolean = accepted(ctx.publish(wrap, ctx.inboxRelays()))

    override suspend fun publishWelcome(delivery: WelcomeDelivery): Boolean {
        val recipient =
            ctx
                .dmInboxOf(delivery.recipientPubKey)
                ?.allRelays()
                ?.toSet()
                .orEmpty()
        return accepted(ctx.publish(delivery.giftWrapEvent, recipient.ifEmpty { ctx.anyRelays() }))
    }

    override suspend fun publishKeyPackage(event: KeyPackageEvent): Boolean = accepted(ctx.publish(event, ctx.keyPackageRelays()))

    override suspend fun fetchOwnKeyPackage(eventId: HexKey): KeyPackageEvent? {
        val filter = Filter(ids = listOf(eventId), authors = listOf(ctx.identity.pubKeyHex), kinds = listOf(KeyPackageEvent.KIND))
        val relays = ctx.keyPackageRelays() + ctx.outboxRelays()
        return ctx.drain(relays.associateWith { listOf(filter) }).firstNotNullOfOrNull { it.second as? KeyPackageEvent }
    }

    override suspend fun ownKeyPackages(): List<KeyPackageEvent>? {
        val filter = Filter(authors = listOf(ctx.identity.pubKeyHex), kinds = listOf(KeyPackageEvent.KIND))
        return ctx
            .drain(ctx.keyPackageRelays().associateWith { listOf(filter) })
            .mapNotNull { it.second as? KeyPackageEvent }
            .groupBy { it.dTag() }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
    }

    override suspend fun publishOwnEvent(event: Event): Boolean = accepted(ctx.publish(event, ctx.outboxRelays() + ctx.keyPackageRelays()))
}
