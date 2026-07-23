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
import com.vitorpamplona.quartz.nip71Video.VideoEvent

/**
 * Media notifications — a picture (kind 20) or video (kinds 21/22/34235/34236)
 * that mentions you. Rendered with BigPictureStyle so the shade shows the actual
 * image (video poster frame) inline. The author's name + avatar are enriched
 * observably; the media URL comes straight off the (already present) event.
 */
object MediaNotification {
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
        val isVideo = event is VideoEvent
        val bigPictureUrl = NotificationContent.mediaImageUrl(event)
        val caption = NotificationContent.excerpt(event.content, 140)

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(author),
            notes = listOf(note),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            val user = author.toBestDisplayName()
            val titleRes =
                if (isVideo) {
                    R.string.app_notification_media_channel_message_video
                } else {
                    R.string.app_notification_media_channel_message_photo
                }
            nm.postStandard(
                category = NotificationCategory.MEDIA,
                id = event.id,
                messageTitle = stringRes(context, titleRes, user),
                messageBody = caption,
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                applicationContext = context,
                bigPictureUrl = bigPictureUrl,
            )
        }
    }
}
