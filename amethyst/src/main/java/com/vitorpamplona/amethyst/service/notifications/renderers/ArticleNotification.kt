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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Article & highlight notifications — long-form (kind 30023), wiki (30818), and
 * NIP-84 highlights (9802) that mention or highlight your writing. Rendered as an
 * indigo card. Highlights show the highlighted passage; long-form/wiki mentions
 * show the excerpt. Author name + avatar enriched observably.
 */
object ArticleNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: Event,
    ) {
        val note = LocalCache.getNoteIfExists(event.id) ?: return
        if (!account.isAcceptable(note)) return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(note, accountNpub)

        val isHighlight = event is HighlightEvent
        val titleRes =
            if (isHighlight) {
                R.string.app_notification_articles_channel_message_highlight
            } else {
                R.string.app_notification_articles_channel_message
            }
        val body =
            if (event is HighlightEvent) {
                NotificationContent.excerpt(event.quote())
            } else {
                NotificationContent.excerpt(event.content)
            }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            users = listOf(author),
            notes = listOf(note),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postStandard(
                category = NotificationCategory.ARTICLE,
                id = event.id,
                messageTitle = stringRes(context, titleRes, author.toBestDisplayName()),
                messageBody = body,
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                applicationContext = context,
            )
        }
    }
}
