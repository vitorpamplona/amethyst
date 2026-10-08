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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.tags.DestinationTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.tags.DevFeeAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.tags.OrderIdTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.tags.PaymentHashTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.NetworkTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PlatformTag

fun TagArrayBuilder<MostroDevFeePaymentEvent>.platform(instanceName: String?) = addUnique(PlatformTag.assemble(PlatformTag.MOSTRO, instanceName))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.documentType() = addUnique(DocumentTypeTag.assemble(DocumentTypeTag.DEV_FEE_PAYMENT))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.orderId(orderId: String) = addUnique(OrderIdTag.assemble(orderId))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.devFeeAmount(sats: Long) = addUnique(DevFeeAmountTag.assemble(sats))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.paymentHash(hash: String) = addUnique(PaymentHashTag.assemble(hash))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.destination(lightningAddress: String) = addUnique(DestinationTag.assemble(lightningAddress))

fun TagArrayBuilder<MostroDevFeePaymentEvent>.network(network: String) = addUnique(NetworkTag.assemble(network))
