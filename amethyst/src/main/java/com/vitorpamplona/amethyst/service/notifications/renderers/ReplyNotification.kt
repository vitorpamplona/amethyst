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
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.InlineReplyTarget
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.ParentMessage
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.replyGroupKeyFor
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.replySummaryIdFor
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Reply notifications — someone replied to your note (NIP-10 kind 1, NIP-22
 * comment kind 1111, or a NIP-28 public-chat reply into your message). Rendered
 * with MessagingStyle: the parent (your) message is shown as prior context, the
 * reply as the latest message, and an inline Reply action lets you answer from
 * the shade. Grouped per-thread so a busy thread collapses into one bundle.
 *
 * The replier's name + avatar are enriched observably.
 */
object ReplyNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: Event,
        parentContent: String?,
        threadRootId: String,
    ) {
        val replyNote = LocalCache.getNoteIfExists(event.id) ?: return
        if (!account.isAcceptable(replyNote)) return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(replyNote, accountNpub)

        val replyExcerpt = NotificationContent.excerpt(event.content)
        val parentExcerpt = parentContent?.let { NotificationContent.excerpt(it, 140) }?.takeIf { it.isNotBlank() }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            users = listOf(author),
            notes = listOf(replyNote),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            val user = author.toBestDisplayName()
            val parent =
                parentExcerpt?.let {
                    ParentMessage(
                        senderName = stringRes(context, R.string.app_notification_me),
                        body = it,
                        pictureUrl = account.userProfile().profilePicture(),
                    )
                }
            nm.postConversation(
                category = NotificationCategory.REPLY,
                id = event.id,
                senderName = user,
                pictureUrl = author.profilePicture(),
                messageBody = replyExcerpt,
                time = event.createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                parent = parent,
                publicInlineReply = InlineReplyTarget(accountNpub = accountNpub, targetEventId = event.id),
                addMarkRead = false,
                groupKey = replyGroupKeyFor(threadRootId),
                summaryId = replySummaryIdFor(threadRootId),
            )
        }
    }
}
