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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.instanceName
import com.vitorpamplona.quartz.nip69P2pOrderEvents.network
import com.vitorpamplona.quartz.nip69P2pOrderEvents.platform
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Mostro instance's public receipt for the development-fee share it paid on one finished trade
 * (kind 8383, regular, so every payment stays on record). Spec: Mostro protocol `other_events.md`
 * ("Development Fee"). Not a NIP.
 *
 * Tags: `order-id`, `amount` (sats), `hash` (Lightning payment hash), `destination` (a Lightning
 * address), `network`, `y`, `z` = `dev-fee-payment`, and a NIP-40 expiration about a year out.
 * Content is empty. The older design in `mostro_dev_fee.md` (Phase 4: `order`, `currency`, `t`
 * tags and JSON content) is not what daemons publish; every live event has the shape above, and
 * that is what is read.
 *
 * Audit data, not human text: not a SearchableEvent. No other app was seen on 8383, so there is no
 * tag split.
 */
@Immutable
class MostroDevFeePaymentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    /** No tag here carries a relay hint. */
    override fun addressHints() = emptyList<AddressHint>()

    /**
     * `ORDER`: the kind-38383 order this fee was paid on. The tag holds only the order's id, but
     * a Mostro instance publishes each order addressable on `d` = that id under the same key that
     * signs this receipt (`mostro_separate_kinds.md`), so `38383:<this pubkey>:<order-id>` is the
     * order's real address. Built only when the id is a UUID, the only form Mostro issues.
     */
    override fun linkedAddressIds() = listOfNotNull(orderAddressId())

    /** The paid order's address, see [linkedAddressIds]. */
    @OptIn(ExperimentalUuidApi::class)
    fun orderAddressId(): String? =
        orderId()
            ?.takeIf { Uuid.parseOrNull(it) != null }
            ?.let { Address.assemble(P2POrderEvent.KIND, pubKey, it) }

    fun platform() = tags.platform()

    fun instanceName() = tags.instanceName()

    fun orderId() = tags.orderId()

    /** The fee paid, in sats. */
    fun amount() = tags.devFeeAmount()

    fun paymentHash() = tags.paymentHash()

    /** The Lightning address the fee went to. */
    fun destination() = tags.destination()

    fun network() = tags.network()

    companion object {
        const val KIND = 8383

        fun build(
            orderId: String,
            amountSats: Long,
            paymentHash: String,
            destination: String,
            network: String,
            expiration: Long? = null,
            instanceName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MostroDevFeePaymentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            orderId(orderId)
            devFeeAmount(amountSats)
            paymentHash(paymentHash)
            destination(destination)
            network(network)
            platform(instanceName)
            documentType()
            expiration?.let { expiration(it) }
            initializer()
        }
    }
}
