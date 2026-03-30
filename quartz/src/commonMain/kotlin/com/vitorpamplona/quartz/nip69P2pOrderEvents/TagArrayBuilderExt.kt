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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.AmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.BondTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.CurrencyTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.ExpiresAtTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.FiatAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.LayerTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.MakerNameTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.NetworkTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatus
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatusTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderType
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PaymentMethodTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PlatformTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PremiumTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.RatingTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.SourceTag

fun TagArrayBuilder<P2POrderEvent>.orderType(type: OrderType) = addUnique(OrderTypeTag.assemble(type))

fun TagArrayBuilder<P2POrderEvent>.orderStatus(status: OrderStatus) = addUnique(OrderStatusTag.assemble(status))

fun TagArrayBuilder<P2POrderEvent>.currency(currency: String) = addUnique(CurrencyTag.assemble(currency))

fun TagArrayBuilder<P2POrderEvent>.amount(amountSats: Long) = addUnique(AmountTag.assemble(amountSats))

fun TagArrayBuilder<P2POrderEvent>.fiatAmount(fiatAmount: FiatAmountTag) = addUnique(FiatAmountTag.assemble(fiatAmount))

fun TagArrayBuilder<P2POrderEvent>.paymentMethods(methods: List<String>) = addUnique(PaymentMethodTag.assemble(methods))

fun TagArrayBuilder<P2POrderEvent>.premium(premium: String) = addUnique(PremiumTag.assemble(premium))

fun TagArrayBuilder<P2POrderEvent>.expiresAt(timestamp: Long) = addUnique(ExpiresAtTag.assemble(timestamp))

fun TagArrayBuilder<P2POrderEvent>.platform(platform: String) = addUnique(PlatformTag.assemble(platform))

fun TagArrayBuilder<P2POrderEvent>.documentType(type: String = DocumentTypeTag.ORDER) = addUnique(DocumentTypeTag.assemble(type))

fun TagArrayBuilder<P2POrderEvent>.source(url: String) = addUnique(SourceTag.assemble(url))

fun TagArrayBuilder<P2POrderEvent>.rating(ratingJson: String) = addUnique(RatingTag.assemble(ratingJson))

fun TagArrayBuilder<P2POrderEvent>.network(network: String) = addUnique(NetworkTag.assemble(network))

fun TagArrayBuilder<P2POrderEvent>.layer(layer: String) = addUnique(LayerTag.assemble(layer))

fun TagArrayBuilder<P2POrderEvent>.makerName(name: String) = addUnique(MakerNameTag.assemble(name))

fun TagArrayBuilder<P2POrderEvent>.geohash(geohash: String) = addAll(GeoHashTag.assemble(geohash).toList())

fun TagArrayBuilder<P2POrderEvent>.bond(bond: Long) = addUnique(BondTag.assemble(bond))
