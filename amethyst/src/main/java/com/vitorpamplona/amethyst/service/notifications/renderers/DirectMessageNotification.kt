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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationContent
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.ReplyAction
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent

/**
 * Direct-message notifications — NIP-17 chat (kind 14), NIP-17 encrypted files
 * (kind 15), and legacy NIP-04 DMs (kind 4). Rendered with MessagingStyle so the
 * shade shows the sender's avatar + name and the message threads under the
 * Conversations section. NIP-17 messages carry an inline Reply action; NIP-04 is
 * read-only (matching the historical behavior).
 *
 * The message body is resolved once up front; only the sender's name + avatar
 * are enriched observably, so a cold-push notification fills in the sender's
 * metadata as the kind:0 lands.
 */
object DirectMessageNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: ChatMessageEvent,
    ) = notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = false)

    suspend fun notify(
        context: Context,
        account: Account,
        event: ChatMessageEncryptedFileHeaderEvent,
    ) = notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = false)

    suspend fun notify(
        context: Context,
        account: Account,
        event: PrivateDmEvent,
    ) {
        if (account.signer.pubKey != event.verifiedRecipientPubKey()) return
        notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = true)
    }

    private suspend fun notifyRoom(
        context: Context,
        account: Account,
        eventId: String,
        createdAt: Long,
        chatRoom: ChatroomKey,
        decrypt: Boolean,
    ) {
        val chatNote = LocalCache.getNoteIfExists(eventId) ?: return
        val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)
        val followingKeySet = account.followingKeySet()

        val isKnownRoom =
            chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true ||
                chatroomList.hasSentMessagesTo(chatRoom)
        if (!isKnownRoom) return

        val author = chatNote.author ?: return
        // Decrypt (NIP-04) or read (NIP-17) the body once — never re-decrypt on
        // each enrichment tick, which could hammer a remote signer.
        val body =
            if (decrypt) {
                NotificationContent.decryptContent(chatNote, account.signer) ?: return
            } else {
                chatNote.event?.content ?: return
            }

        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(chatNote, accountNpub)
        val replyAction =
            if (decrypt) {
                null // NIP-04 is read-only in the tray
            } else {
                ReplyAction.Dm(accountNpub = accountNpub, chatroomMembers = chatRoom.users.joinToString(","))
            }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            users = listOf(author),
            notes = listOf(chatNote),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postConversation(
                category = NotificationCategory.DIRECT_MESSAGE,
                id = eventId,
                senderName = author.toBestDisplayName(),
                pictureUrl = author.profilePicture(),
                messageBody = body,
                time = createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                replyAction = replyAction,
            )
        }
    }
}
