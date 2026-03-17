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
package com.vitorpamplona.quartz.nip47WalletConnect.rpc

import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

object NwcNotificationType {
    const val PAYMENT_RECEIVED = "payment_received"
    const val PAYMENT_SENT = "payment_sent"
    const val HOLD_INVOICE_ACCEPTED = "hold_invoice_accepted"
}

// NOTIFICATION OBJECTS
abstract class Notification(
    val notification_type: String,
) : OptimizedSerializable

// payment_received notification
class PaymentReceivedNotification(
    val notification: NwcTransaction? = null,
) : Notification(NwcNotificationType.PAYMENT_RECEIVED)

// payment_sent notification
class PaymentSentNotification(
    val notification: NwcTransaction? = null,
) : Notification(NwcNotificationType.PAYMENT_SENT)

// hold_invoice_accepted notification
class HoldInvoiceAcceptedNotification(
    val notification: HoldInvoiceAcceptedData? = null,
) : Notification(NwcNotificationType.HOLD_INVOICE_ACCEPTED)

class HoldInvoiceAcceptedData(
    var type: String? = null,
    var invoice: String? = null,
    var payment_hash: String? = null,
    var amount: Long? = null,
    var created_at: Long? = null,
    var expires_at: Long? = null,
    var settle_deadline: Long? = null,
)
