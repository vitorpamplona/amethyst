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
package com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization

import com.vitorpamplona.quartz.nip47WalletConnect.HoldInvoiceAcceptedData
import com.vitorpamplona.quartz.nip47WalletConnect.HoldInvoiceAcceptedNotification
import com.vitorpamplona.quartz.nip47WalletConnect.Notification
import com.vitorpamplona.quartz.nip47WalletConnect.NwcNotificationType
import com.vitorpamplona.quartz.nip47WalletConnect.PaymentReceivedNotification
import com.vitorpamplona.quartz.nip47WalletConnect.PaymentSentNotification
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object Nip47NotificationKSerializer : KSerializer<Notification> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Nip47Notification")

    override fun serialize(
        encoder: Encoder,
        value: Notification,
    ) {
        throw UnsupportedOperationException("NIP-47 Notification serialization not supported")
    }

    override fun deserialize(decoder: Decoder): Notification {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val notificationType =
            jsonObject["notification_type"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

        return when (notificationType) {
            NwcNotificationType.PAYMENT_RECEIVED -> {
                PaymentReceivedNotification(
                    notification =
                        Nip47ResponseKSerializer.parseTransaction(
                            jsonObject["notification"]?.jsonObject,
                        ),
                )
            }
            NwcNotificationType.PAYMENT_SENT -> {
                PaymentSentNotification(
                    notification =
                        Nip47ResponseKSerializer.parseTransaction(
                            jsonObject["notification"]?.jsonObject,
                        ),
                )
            }
            NwcNotificationType.HOLD_INVOICE_ACCEPTED -> {
                val notifObj = jsonObject["notification"]?.jsonObject
                HoldInvoiceAcceptedNotification(
                    notification =
                        notifObj?.let {
                            HoldInvoiceAcceptedData(
                                type = it["type"]?.jsonPrimitive?.content,
                                invoice = it["invoice"]?.jsonPrimitive?.content,
                                payment_hash = it["payment_hash"]?.jsonPrimitive?.content,
                                amount = it["amount"]?.jsonPrimitive?.longOrNull,
                                created_at = it["created_at"]?.jsonPrimitive?.longOrNull,
                                expires_at = it["expires_at"]?.jsonPrimitive?.longOrNull,
                                settle_deadline = it["settle_deadline"]?.jsonPrimitive?.longOrNull,
                            )
                        },
                )
            }
            else -> throw IllegalArgumentException("Unknown notification type: $notificationType")
        }
    }
}
