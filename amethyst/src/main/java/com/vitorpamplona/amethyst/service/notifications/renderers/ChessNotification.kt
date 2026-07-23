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
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent

/**
 * Chess game notifications — NIP-64 challenge-accepted and move events. Rendered
 * as a brown card ("Chess" title, "X accepted your challenge" / "X moved — your
 * turn"). Opponent name + avatar enriched observably.
 */
object ChessNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: BaseChessEvent,
        @StringRes contentRes: Int,
    ) {
        val author = LocalCache.getOrCreateUser(event.pubKey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.notificationsUri(accountNpub, event.id)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(author),
            notes = emptyList(),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postStandard(
                category = NotificationCategory.CHESS,
                id = event.id,
                messageTitle = stringRes(context, R.string.app_notification_chess_channel_name),
                messageBody = stringRes(context, contentRes, author.toBestDisplayName()),
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                applicationContext = context,
            )
        }
    }
}
