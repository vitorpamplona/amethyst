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
import com.vitorpamplona.amethyst.service.notifications.NotificationContent
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Text-mention notifications — someone mentioned, quoted, or cited you in a note
 * (kind 1), or asked a poll that tags you. Rendered as an accented BigText card
 * titled "X mentioned you" with the post excerpt. The author's name + avatar and
 * the post body are enriched observably.
 *
 * Media (picture/video), articles/highlights, and git events are richer and live
 * in their own renderers; this covers plain text mentions and polls.
 */
object MentionNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: Event,
        category: NotificationCategory = NotificationCategory.MENTION,
        @StringRes titleRes: Int = R.string.app_notification_mentions_channel_message,
    ) {
        val note = LocalCache.getNoteIfExists(event.id) ?: return
        if (!account.isAcceptable(note)) return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(note, accountNpub)
        val body = NotificationContent.excerpt(event.content)

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(author),
            notes = listOf(note),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postStandard(
                category = category,
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
