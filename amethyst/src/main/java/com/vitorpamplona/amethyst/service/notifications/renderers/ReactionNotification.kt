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
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji

/**
 * Reaction (like) notifications — kind 7. Rendered as a heart-accented card
 * titled with the reactor's chosen emoji + name; the reacted-post excerpt is the
 * body. NIP-30 custom emoji, which can't render as text, are shown as a badge
 * overlaid on the reactor's avatar. The reactor's name + avatar and the reacted
 * post's content are enriched observably.
 */
object ReactionNotification {
    private const val LIKE_EMOJI = "🤙" // 🤙
    private const val DISLIKE_EMOJI = "👎" // 👎

    suspend fun notify(
        context: Context,
        account: Account,
        event: ReactionEvent,
    ) {
        // NIP-25: the LAST `e` tag is the note actually reacted to.
        val reactedPostId = event.originalPost().lastOrNull() ?: return
        val reactedNote = LocalCache.checkGetOrCreateNote(reactedPostId)
        if (reactedNote != null && !account.isAcceptable(reactedNote)) return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val reactionContent = event.content
        val customEmojiUrl = CustomEmoji.createEmojiMap(event.tags)[reactionContent]

        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.notificationsUri(accountNpub, event.id)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            users = listOf(author),
            notes = listOfNotNull(reactedNote),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            val user = author.toBestDisplayName()
            val title =
                if (customEmojiUrl != null) {
                    user
                } else {
                    "${symbolFor(reactionContent)} $user"
                }
            val reactedContent = NotificationContent.excerpt(reactedNote?.event?.content, 140)
            val body =
                if (reactedContent.isNotBlank()) {
                    stringRes(context, R.string.app_notification_reactions_channel_message_for, reactedContent)
                } else {
                    stringRes(context, R.string.app_notification_reactions_channel_message, user)
                }
            nm.postStandard(
                category = NotificationCategory.REACTION,
                id = event.id,
                messageTitle = title,
                messageBody = body,
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                applicationContext = context,
                badgeUrl = customEmojiUrl,
            )
        }
    }

    private fun symbolFor(content: String): String =
        when {
            content == ReactionEvent.LIKE || content.isBlank() -> LIKE_EMOJI
            content == ReactionEvent.DISLIKE -> DISLIKE_EMOJI
            else -> content
        }
}
