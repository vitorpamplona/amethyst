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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.AmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.BondTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.CurrencyTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.ExpiresAtTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.FiatAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.LayerTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.MakerNameTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.NetworkTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatusTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PaymentMethodTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PlatformTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PremiumTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.RatingTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.SourceTag

fun TagArray.orderType() = firstNotNullOfOrNull(OrderTypeTag::parse)

fun TagArray.orderStatus() = firstNotNullOfOrNull(OrderStatusTag::parse)

fun TagArray.currency() = firstNotNullOfOrNull(CurrencyTag::parse)

fun TagArray.amount() = firstNotNullOfOrNull(AmountTag::parse)

fun TagArray.fiatAmount() = firstNotNullOfOrNull(FiatAmountTag::parse)

fun TagArray.paymentMethods() = firstNotNullOfOrNull(PaymentMethodTag::parse)

fun TagArray.premium() = firstNotNullOfOrNull(PremiumTag::parse)

fun TagArray.expiresAt() = firstNotNullOfOrNull(ExpiresAtTag::parse)

fun TagArray.platform() = firstNotNullOfOrNull(PlatformTag::parse)

fun TagArray.documentType() = firstNotNullOfOrNull(DocumentTypeTag::parse)

fun TagArray.source() = firstNotNullOfOrNull(SourceTag::parse)

fun TagArray.rating() = firstNotNullOfOrNull(RatingTag::parse)

fun TagArray.network() = firstNotNullOfOrNull(NetworkTag::parse)

fun TagArray.layer() = firstNotNullOfOrNull(LayerTag::parse)

fun TagArray.makerName() = firstNotNullOfOrNull(MakerNameTag::parse)

fun TagArray.bond() = firstNotNullOfOrNull(BondTag::parse)
