/**
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
package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

class EventNotificationConsumer(
    private val applicationContext: Context,
) {
    companion object {
        const val TAG = "EventNotificationConsumer"
    }

    suspend fun consume(event: GiftWrapEvent) {
        Log.d(TAG, "New Notification Arrived")

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        var matchAccount = false
        LocalPreferences.allSavedAccounts().forEach {
            if (!matchAccount && (it.hasPrivKey || it.loggedInWithExternalSigner)) {
                LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)?.let { acc ->
                    Log.d(TAG, "New Notification Testing if for ${it.npub}")
                    try {
                        consumeIfMatchesAccount(event, acc)
                        matchAccount = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG, "Message was not for user ${it.npub}: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun consumeIfMatchesAccount(
        pushWrappedEvent: GiftWrapEvent,
        account: AccountSettings,
    ) {
        val signer = account.createSigner(applicationContext.contentResolver)

        val notificationEvent = pushWrappedEvent.unwrapThrowing(signer)
        consumeNotificationEvent(notificationEvent, signer, account)
    }

    suspend fun consumeNotificationEvent(
        notificationEvent: Event,
        signer: NostrSigner,
        account: AccountSettings,
    ) {
        val consumed = LocalCache.hasConsumed(notificationEvent)
        Log.d(TAG, "New Notification ${notificationEvent.kind} ${notificationEvent.id} Arrived for ${signer.pubKey} consumed= $consumed")
        if (!consumed) {
            Log.d(TAG, "New Notification was verified")
            if (!notificationManager().areNotificationsEnabled()) return
            Log.d(TAG, "Notifications are enabled")

            unwrapAndConsume(notificationEvent, signer)?.let { innerEvent ->
                Log.d(TAG, "Unwrapped consume ${innerEvent.javaClass.simpleName}")

                when (innerEvent) {
                    is PrivateDmEvent -> notify(innerEvent, signer, account)
                    is LnZapEvent -> notify(innerEvent, signer, account)
                    is ChatMessageEvent -> notify(innerEvent, signer, account)
                    is ChatMessageEncryptedFileHeaderEvent -> notify(innerEvent, signer, account)
                }
            }
        }
    }

    suspend fun findAccountAndConsume(event: Event) {
        Log.d(TAG, "New Notification Arrived")
        val users = event.taggedUserIds().map { LocalCache.getOrCreateUser(it) }
        val npubs = users.map { it.pubkeyNpub() }.toSet()

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        var matchAccount = false
        LocalPreferences.allSavedAccounts().forEach {
            if (!matchAccount && (it.hasPrivKey || it.loggedInWithExternalSigner) && it.npub in npubs) {
                LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)?.let { acc ->
                    Log.d(TAG, "New Notification Testing if for ${it.npub}")
                    try {
                        val signer = acc.createSigner(applicationContext.contentResolver)
                        consumeNotificationEvent(event, signer, acc)
                        matchAccount = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG, "Message was not for user ${it.npub}: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun unwrapAndConsume(
        event: Event,
        signer: NostrSigner,
    ): Event? {
        if (LocalCache.hasConsumed(event)) return null

        return when (event) {
            is GiftWrapEvent -> {
                if (LocalCache.justConsume(event, null, false)) {
                    // new event
                    val inner = event.unwrapThrowing(signer)
                    // clear the encrypted payload to save memory
                    LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

                    unwrapAndConsume(inner, signer)
                } else {
                    null
                }
            }
            is SealedRumorEvent -> {
                if (LocalCache.justConsume(event, null, false)) {
                    // new event
                    val inner = event.unsealThrowing(signer)
                    // clear the encrypted payload to save memory
                    LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

                    // this is not verifiable
                    if (LocalCache.justConsume(inner, null, true)) {
                        event
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            else -> {
                LocalCache.justConsume(event, null, false)
                event
            }
        }
    }

    private fun notify(
        event: ChatMessageEncryptedFileHeaderEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        Log.d(TAG, "New ChatMessage File to Notify")
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val chatroomList = LocalCache.getOrCreateChatroomList(signer.pubKey)
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(signer.pubKey)

            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val isKnownRoom =
                (
                    chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)
                )

            if (isKnownRoom) {
                val content = chatNote.event?.content ?: ""
                val user = chatNote.author?.toBestDisplayName() ?: ""
                val userPicture = chatNote.author?.profilePicture()
                val noteUri = chatNote.toNEvent() + "?account=" + acc.keyPair.pubKey.toNpub()

                // TODO: Show Image on notification
                notificationManager()
                    .sendDMNotification(
                        event.id,
                        content,
                        user,
                        event.createdAt,
                        userPicture,
                        noteUri,
                        applicationContext,
                    )
            }
        }
    }

    private fun notify(
        event: ChatMessageEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        Log.d(TAG, "New ChatMessage to Notify")
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val chatroomList = LocalCache.getOrCreateChatroomList(signer.pubKey)
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(signer.pubKey)

            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val isKnownRoom = chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)

            if (isKnownRoom) {
                val content = chatNote.event?.content ?: ""
                val user = chatNote.author?.toBestDisplayName() ?: ""
                val userPicture = chatNote.author?.profilePicture()
                val noteUri = chatNote.toNEvent() + "?account=" + acc.keyPair.pubKey.toNpub()
                notificationManager()
                    .sendDMNotification(
                        event.id,
                        content,
                        user,
                        event.createdAt,
                        userPicture,
                        noteUri,
                        applicationContext,
                    )
            }
        }
    }

    private suspend fun notify(
        event: PrivateDmEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        Log.d(TAG, "New Nip-04 DM to Notify")
        val note = LocalCache.getNoteIfExists(event.id) ?: return
        val chatroomList = LocalCache.getOrCreateChatroomList(signer.pubKey)

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        if (signer.pubKey == event.verifiedRecipientPubKey()) {
            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val chatRoom = event.chatroomKey(signer.pubKey)

            val isKnownRoom = chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)

            if (isKnownRoom) {
                note.author?.let {
                    decryptContent(note, signer)?.let { content ->
                        val user = note.author?.toBestDisplayName() ?: ""
                        val userPicture = note.author?.profilePicture()
                        val noteUri = note.toNEvent() + "?account=" + acc.keyPair.pubKey.toNpub()
                        notificationManager()
                            .sendDMNotification(event.id, content, user, event.createdAt, userPicture, noteUri, applicationContext)
                    }
                }
            }
        }
    }

    suspend fun decryptZapContentAuthor(
        event: LnZapRequestEvent,
        signer: NostrSigner,
    ): Event? =
        if (event.isPrivateZap() && event.zappedAuthor().contains(event.pubKey)) {
            signer.decryptZapEvent(event)
        } else {
            event
        }

    suspend fun decryptContent(
        note: Note,
        signer: NostrSigner,
    ): String? {
        val event = note.event
        when (event) {
            is PrivateDmEvent -> {
                return event.decryptContent(signer)
            }

            is LnZapRequestEvent -> {
                return decryptZapContentAuthor(event, signer)?.content
            }

            else -> {
                return event?.content
            }
        }
    }

    private suspend fun notify(
        event: LnZapEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        Log.d(TAG, "New Zap to Notify")
        Log.d(TAG, "Notify Start ${event.toNostrUri()}")
        LocalCache.getNoteIfExists(event.id) ?: return

        Log.d(TAG, "Notify Not Notified Yet")

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        Log.d(TAG, "Notify Not an old event")

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val noteZapped = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return

        Log.d(TAG, "Notify ZapRequest $noteZapRequest zapped $noteZapped")

        if ((event.amount ?: BigDecimal.ZERO) < BigDecimal.TEN) return

        Log.d(TAG, "Notify Amount Bigger than 10")

        if (event.isTaggedUser(signer.pubKey)) {
            val amount = showAmount(event.amount)

            Log.d(TAG, "Notify Amount $amount")

            (noteZapRequest.event as? LnZapRequestEvent)?.let { event ->
                decryptZapContentAuthor(event, signer)?.let { decryptedEvent ->
                    Log.d(TAG, "Notify Decrypted if Private Zap ${event.id}")

                    val author = LocalCache.getOrCreateUser(decryptedEvent.pubKey)
                    val senderInfo = Pair(author, decryptedEvent.content.ifBlank { null })

                    if (noteZapped.event?.content != null) {
                        decryptContent(noteZapped, signer)?.let { decrypted ->
                            Log.d(TAG, "Notify Decrypted if Private Note")

                            val zappedContent = decrypted.split("\n")[0]

                            val user = senderInfo.first.toBestDisplayName()
                            var title = stringRes(applicationContext, R.string.app_notification_zaps_channel_message, amount)
                            senderInfo.second?.ifBlank { null }?.let { title += " ($it)" }

                            var content =
                                stringRes(
                                    applicationContext,
                                    R.string.app_notification_zaps_channel_message_from,
                                    user,
                                )
                            zappedContent.let {
                                content +=
                                    " " +
                                    stringRes(
                                        applicationContext,
                                        R.string.app_notification_zaps_channel_message_for,
                                        zappedContent,
                                    )
                            }
                            val userPicture = senderInfo.first.profilePicture()
                            val noteUri = "notifications?account=" + acc.keyPair.pubKey.toNpub()

                            Log.d(TAG, "Notify ${event.id} $content $title $noteUri")

                            notificationManager()
                                .sendZapNotification(
                                    event.id,
                                    content,
                                    title,
                                    event.createdAt,
                                    userPicture,
                                    noteUri,
                                    applicationContext,
                                )
                        }
                    } else {
                        // doesn't have a base note to refer to.
                        Log.d(TAG, "Notify Zapped note not available")

                        val user = senderInfo.first.toBestDisplayName()
                        var title = stringRes(applicationContext, R.string.app_notification_zaps_channel_message, amount)
                        senderInfo.second?.ifBlank { null }?.let { title += " ($it)" }

                        val content =
                            stringRes(
                                applicationContext,
                                R.string.app_notification_zaps_channel_message_from,
                                user,
                            )

                        val userPicture = senderInfo.first.profilePicture()
                        val noteUri = "notifications?account=" + acc.keyPair.pubKey.toNpub()

                        Log.d(TAG, "Notify ${event.id} $title $noteUri")

                        notificationManager()
                            .sendZapNotification(
                                event.id,
                                content,
                                title,
                                event.createdAt,
                                userPicture,
                                noteUri,
                                applicationContext,
                            )
                    }
                }
            }
        }
    }

    fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            as NotificationManager
}
