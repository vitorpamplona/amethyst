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
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationContent
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

/**
 * Repost / boost notifications — NIP-18 kind 6 and kind 16. Rendered as a
 * green-accented card: "X reposted your post" with the reposted excerpt. The
 * booster's name + avatar and the boosted post's content are enriched observably.
 */
object RepostNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: RepostEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, event.boostedEventId())

    suspend fun notify(
        context: Context,
        account: Account,
        event: GenericRepostEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, event.boostedEventId())

    private suspend fun post(
        context: Context,
        account: Account,
        id: String,
        createdAt: Long,
        boosterPubkey: String,
        boostedEventId: String?,
    ) {
        val boostedNote = boostedEventId?.let { LocalCache.checkGetOrCreateNote(it) }
        if (boostedNote != null && !account.isAcceptable(boostedNote)) return

        val booster = LocalCache.getOrCreateUser(boosterPubkey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.notificationsUri(accountNpub, id)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            users = listOf(booster),
            notes = listOfNotNull(boostedNote),
            isComplete = { booster.metadataOrNull()?.bestName() != null },
        ) {
            nm.postStandard(
                category = NotificationCategory.REPOST,
                id = id,
                messageTitle = stringRes(context, R.string.app_notification_reposts_channel_message, booster.toBestDisplayName()),
                messageBody = NotificationContent.excerpt(boostedNote?.event?.content, 140),
                time = createdAt,
                pictureUrl = booster.profilePicture(),
                uri = uri,
                applicationContext = context,
            )
        }
    }
}
