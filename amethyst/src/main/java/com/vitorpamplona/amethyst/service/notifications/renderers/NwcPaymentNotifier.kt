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
package com.vitorpamplona.amethyst.service.notifications.renderers

import android.content.Context
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.shared.R
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Posts a tray notification for an incoming Lightning payment reported by the
 * connected NWC wallet (NIP-47 `payment_received`). Renders on the green Payments
 * channel with a wallet icon; the title leads with the amount.
 *
 * Zaps are intentionally NOT routed here — a `payment_received` whose transaction
 * metadata carries a NIP-57 zap request is filtered out upstream by
 * [com.vitorpamplona.amethyst.service.notifications.NwcPaymentNotificationWatcher],
 * because those already surface through the kind-9735 [ZapNotification] path.
 */
object NwcPaymentNotifier {
    suspend fun notify(
        context: Context,
        account: Account,
        tx: NwcTransaction,
    ) {
        val nm = context.notificationManager()
        if (!nm.areNotificationsEnabled()) return

        val msats = tx.amount ?: return
        val amount = showAmount((msats / 1000L).toBigDecimal())

        val id = tx.payment_hash ?: tx.invoice ?: tx.created_at?.toString() ?: return
        val time = tx.settled_at ?: tx.created_at ?: TimeUtils.now()

        val title = stringRes(context, R.string.app_notification_payments_channel_message, amount)
        val comment = (tx.parsedMetadata()?.comment ?: tx.description)?.ifBlank { null }
        val body = comment ?: title

        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.notificationsUri(accountNpub, id)

        nm.postStandard(
            category = NotificationCategory.PAYMENT_RECEIVED,
            id = id,
            messageTitle = title,
            messageBody = body,
            time = time,
            pictureUrl = null,
            uri = uri,
            applicationContext = context,
        )
    }
}
