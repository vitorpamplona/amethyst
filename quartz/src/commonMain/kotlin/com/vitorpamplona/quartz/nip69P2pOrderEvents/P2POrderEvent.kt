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
package com.vitorpamplona.quartz.nip69P2pOrderEvents

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.FiatAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatus
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class P2POrderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun orderType() = tags.orderType()

    fun currency() = tags.currency()

    fun status() = tags.orderStatus()

    fun amount() = tags.amount()

    fun fiatAmount() = tags.fiatAmount()

    fun paymentMethods() = tags.paymentMethods()

    fun premium() = tags.premium()

    fun expiresAt() = tags.expiresAt()

    fun platform() = tags.platform()

    fun documentType() = tags.documentType()

    fun source() = tags.source()

    fun rating() = tags.rating()

    fun network() = tags.network()

    fun layer() = tags.layer()

    fun makerName() = tags.makerName()

    fun bond() = tags.bond()

    companion object {
        const val KIND = 38383
        const val ALT_DESCRIPTION = "P2P Order"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            orderType: OrderType,
            currency: String,
            status: OrderStatus,
            amount: Long,
            fiatAmount: FiatAmountTag,
            paymentMethods: List<String>,
            premium: String,
            platform: String,
            expiresAt: Long,
            expiration: Long? = null,
            source: String? = null,
            ratingJson: String? = null,
            network: String? = null,
            layer: String? = null,
            makerName: String? = null,
            geohash: String? = null,
            bond: Long? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<P2POrderEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            orderType(orderType)
            currency(currency)
            orderStatus(status)
            amount(amount)
            fiatAmount(fiatAmount)
            paymentMethods(paymentMethods)
            premium(premium)
            expiresAt(expiresAt)
            expiration?.let { expiration(it) }
            platform(platform)
            documentType()
            source?.let { source(it) }
            ratingJson?.let { rating(it) }
            network?.let { network(it) }
            layer?.let { layer(it) }
            makerName?.let { makerName(it) }
            geohash?.let { geohash(it) }
            bond?.let { bond(it) }
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
