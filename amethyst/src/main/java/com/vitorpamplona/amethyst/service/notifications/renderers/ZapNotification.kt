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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationContent
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import java.math.BigDecimal

/**
 * Zap notifications — Lightning (NIP-57, kind 9735), Cashu nutzaps (NIP-61, kind
 * 9321), and onchain zaps (kind 8333). All render on the gold Zaps channel with a
 * bolt icon; the title leads with the amount, the body names the sender and the
 * zapped-post excerpt. The sender's name + avatar are enriched observably; for
 * private Lightning zaps the sender is decrypted once up front.
 */
object ZapNotification {
    private val MIN_ZAP_AMOUNT = BigDecimal.TEN

    suspend fun notify(
        context: Context,
        account: Account,
        event: LnZapEvent,
    ) {
        LocalCache.getNoteIfExists(event.id) ?: return
        val zapRequestNote = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val zappedNote = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        if (!account.isAcceptable(zappedNote)) return
        if ((event.amount ?: BigDecimal.ZERO) < MIN_ZAP_AMOUNT) return

        val zapRequestEvent = zapRequestNote.event as? LnZapRequestEvent ?: return
        // Resolve the (possibly private) zapper once — never re-decrypt per tick.
        val decrypted = NotificationContent.decryptZapContentAuthor(zapRequestEvent, account.signer) ?: return
        val sender = LocalCache.getOrCreateUser(decrypted.pubKey)
        val comment = decrypted.content.ifBlank { null }
        val amount = showAmount(event.amount)

        post(
            context = context,
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { _ -> zapTitle(context, amount, comment) },
            body = { user, excerpt -> fromLine(context, R.string.app_notification_zaps_channel_message_from, user, excerpt) },
        )
    }

    suspend fun notify(
        context: Context,
        account: Account,
        event: NutzapEvent,
    ) {
        val zappedNote = event.linkedEventIds().lastOrNull()?.let { LocalCache.checkGetOrCreateNote(it) }
        if (zappedNote != null && !account.isAcceptable(zappedNote)) return
        val sender = LocalCache.getOrCreateUser(event.pubKey)

        post(
            context = context,
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { user -> stringRes(context, R.string.app_notification_nutzap_channel_message_from, user) },
            body = { user, excerpt -> excerpt.ifBlank { user } },
        )
    }

    suspend fun notify(
        context: Context,
        account: Account,
        event: OnchainZapEvent,
    ) {
        val zappedNote = event.zappedEvent()?.let { LocalCache.checkGetOrCreateNote(it) }
        if (zappedNote != null && !account.isAcceptable(zappedNote)) return
        val sender = LocalCache.getOrCreateUser(event.pubKey)
        val sats = event.claimedAmountInSats()

        post(
            context = context,
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { user ->
                if (sats != null) {
                    stringRes(context, R.string.app_notification_zaps_channel_message, showAmount(sats.toBigDecimal()))
                } else {
                    stringRes(context, R.string.app_notification_onchain_channel_message_from, user)
                }
            },
            body = { user, excerpt -> fromLine(context, R.string.app_notification_onchain_channel_message_from, user, excerpt) },
        )
    }

    private suspend fun post(
        context: Context,
        account: Account,
        id: String,
        createdAt: Long,
        sender: User,
        zappedNote: Note?,
        title: (String) -> String,
        body: (String, String) -> String,
    ) {
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.notificationsUri(accountNpub, id)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = id,
            users = listOf(sender),
            notes = listOfNotNull(zappedNote),
            isComplete = { sender.metadataOrNull()?.bestName() != null },
        ) {
            val user = sender.toBestDisplayName()
            val excerpt =
                zappedNote?.let { NotificationContent.excerpt(NotificationContent.decryptContent(it, account.signer), 140) } ?: ""
            nm.postStandard(
                category = NotificationCategory.ZAP,
                id = id,
                messageTitle = title(user),
                messageBody = body(user, excerpt),
                time = createdAt,
                pictureUrl = sender.profilePicture(),
                uri = uri,
                applicationContext = context,
            )
        }
    }

    private fun zapTitle(
        context: Context,
        amount: String,
        comment: String?,
    ): String {
        val base = stringRes(context, R.string.app_notification_zaps_channel_message, amount)
        return if (comment != null) "$base ($comment)" else base
    }

    private fun fromLine(
        context: Context,
        fromRes: Int,
        user: String,
        excerpt: String,
    ): String {
        var content = stringRes(context, fromRes, user)
        if (excerpt.isNotBlank()) {
            content += " " + stringRes(context, R.string.app_notification_zaps_channel_message_for, excerpt)
        }
        return content
    }
}
