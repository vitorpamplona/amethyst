/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerExternal
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
        if (!LocalCache.justVerify(event)) return

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
        // TODO: Modify the external launcher to launch as different users.
        // Right now it only registers if Amber has already approved this signature
        val signer = account.createSigner()
        if (signer is NostrSignerExternal) {
            signer.launcher.registerLauncher(
                launcher = { },
                contentResolver = Amethyst.instance::contentResolverFn,
            )
        }

        pushWrappedEvent.unwrapThrowing(signer) { notificationEvent ->
            consumeNotificationEvent(notificationEvent, signer, account)
        }
    }

    fun consumeNotificationEvent(
        notificationEvent: Event,
        signer: NostrSigner,
        account: AccountSettings,
    ) {
        val consumed = LocalCache.hasConsumed(notificationEvent)
        val verified = LocalCache.justVerify(notificationEvent)
        Log.d(TAG, "New Notification ${notificationEvent.kind} ${notificationEvent.id} Arrived for ${signer.pubKey} consumed= $consumed && verified= $verified")
        if (!consumed && verified) {
            Log.d(TAG, "New Notification was verified")
            unwrapAndConsume(notificationEvent, signer) { innerEvent ->
                if (!notificationManager().areNotificationsEnabled()) return@unwrapAndConsume

                Log.d(TAG, "Unwrapped consume $consumed ${innerEvent.javaClass.simpleName}")
                if (innerEvent is PrivateDmEvent) {
                    Log.d(TAG, "New Nip-04 DM to Notify")
                    notify(innerEvent, signer, account)
                } else if (innerEvent is LnZapEvent) {
                    Log.d(TAG, "New Zap to Notify")
                    notify(innerEvent, signer, account)
                } else if (innerEvent is ChatMessageEvent) {
                    Log.d(TAG, "New ChatMessage to Notify")
                    notify(innerEvent, signer, account)
                } else if (innerEvent is ChatMessageEncryptedFileHeaderEvent) {
                    Log.d(TAG, "New ChatMessage File to Notify")
                    notify(innerEvent, signer, account)
                }
            }
        }
    }

    suspend fun findAccountAndConsume(event: Event) {
        Log.d(TAG, "New Notification Arrived")
        if (!LocalCache.justVerify(event)) return

        val users = event.taggedUsers().map { LocalCache.getOrCreateUser(it) }
        val npubs = users.map { it.pubkeyNpub() }.toSet()

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        var matchAccount = false
        LocalPreferences.allSavedAccounts().forEach {
            if (!matchAccount && (it.hasPrivKey || it.loggedInWithExternalSigner) && it.npub in npubs) {
                LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)?.let { acc ->
                    Log.d(TAG, "New Notification Testing if for ${it.npub}")
                    try {
                        // TODO: Modify the external launcher to launch as different users.
                        // Right now it only registers if Amber has already approved this signature
                        val signer = acc.createSigner()
                        if (signer is NostrSignerExternal) {
                            signer.launcher.registerLauncher(
                                launcher = { },
                                contentResolver = Amethyst.instance::contentResolverFn,
                            )
                        }

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

    private fun unwrapAndConsume(
        event: Event,
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        if (!LocalCache.justVerify(event)) return
        if (LocalCache.hasConsumed(event)) return

        when (event) {
            is GiftWrapEvent -> {
                event.unwrap(signer) {
                    unwrapAndConsume(it, signer, onReady)
                    LocalCache.justConsume(event, null)
                }
            }
            is SealedGossipEvent -> {
                event.unseal(signer) {
                    if (!LocalCache.hasConsumed(it)) {
                        // this is not verifiable
                        LocalCache.justConsume(it, null)
                        onReady(it)
                    }
                    LocalCache.justConsume(event, null)
                }
            }
            else -> {
                LocalCache.justConsume(event, null)
                onReady(event)
            }
        }
    }

    private fun notify(
        event: ChatMessageEncryptedFileHeaderEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val myUser = LocalCache.getUserIfExists(signer.pubKey) ?: return
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(signer.pubKey)

            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val isKnownRoom =
                (
                    myUser.privateChatrooms[chatRoom]?.senderIntersects(followingKeySet) == true ||
                        myUser.hasSentMessagesTo(chatRoom)
                )

            if (isKnownRoom) {
                val content = chatNote.event?.content() ?: ""
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
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val myUser = LocalCache.getUserIfExists(signer.pubKey) ?: return
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(signer.pubKey)

            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val isKnownRoom =
                (
                    myUser.privateChatrooms[chatRoom]?.senderIntersects(followingKeySet) == true ||
                        myUser.hasSentMessagesTo(chatRoom)
                )

            if (isKnownRoom) {
                val content = chatNote.event?.content() ?: ""
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

    private fun notify(
        event: PrivateDmEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        val note = LocalCache.getNoteIfExists(event.id) ?: return
        val myUser = LocalCache.getUserIfExists(signer.pubKey) ?: return

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        if (signer.pubKey == event.verifiedRecipientPubKey()) {
            val followingKeySet = acc.backupContactList?.unverifiedFollowKeySet()?.toSet() ?: return

            val chatRoom = event.chatroomKey(signer.pubKey)

            val isKnownRoom =
                myUser.privateChatrooms[chatRoom]?.senderIntersects(followingKeySet) == true ||
                    myUser.hasSentMessagesTo(chatRoom)

            if (isKnownRoom) {
                note.author?.let {
                    decryptContent(note, signer) { content ->
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

    fun decryptZapContentAuthor(
        note: Note,
        signer: NostrSigner,
        onReady: (Event) -> Unit,
    ) {
        val event = note.event
        if (event is LnZapRequestEvent) {
            if (event.isPrivateZap()) {
                event.decryptPrivateZap(signer) { onReady(it) }
            } else {
                onReady(event)
            }
        }
    }

    fun decryptContent(
        note: Note,
        signer: NostrSigner,
        onReady: (String) -> Unit,
    ) {
        val event = note.event
        if (event is PrivateDmEvent) {
            event.plainContent(signer, onReady)
        } else if (event is LnZapRequestEvent) {
            decryptZapContentAuthor(note, signer) { onReady(it.content) }
        } else if (event is DraftEvent) {
            event.cachedDraft(signer) {
                onReady(it.content)
            }
        } else {
            event?.content()?.let { onReady(it) }
        }
    }

    private fun notify(
        event: LnZapEvent,
        signer: NostrSigner,
        acc: AccountSettings,
    ) {
        Log.d(TAG, "Notify Start ${event.toNostrUri()}")
        val noteZapEvent = LocalCache.getNoteIfExists(event.id) ?: return

        Log.d(TAG, "Notify Not Notified Yet")

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        Log.d(TAG, "Notify Not an old event")

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val noteZapped =
            event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return

        Log.d(TAG, "Notify ZapRequest $noteZapRequest zapped $noteZapped")

        if ((event.amount ?: BigDecimal.ZERO) < BigDecimal.TEN) return

        Log.d(TAG, "Notify Amount Bigger than 10")

        if (event.isTaggedUser(signer.pubKey)) {
            val amount = showAmount(event.amount)

            Log.d(TAG, "Notify Amount $amount")

            (noteZapRequest.event as? LnZapRequestEvent)?.let { event ->
                decryptZapContentAuthor(noteZapRequest, signer) {
                    Log.d(TAG, "Notify Decrypted if Private Zap ${event.id}")

                    val author = LocalCache.getOrCreateUser(it.pubKey)
                    val senderInfo = Pair(author, it.content.ifBlank { null })

                    if (noteZapped.event?.content() != null) {
                        decryptContent(noteZapped, signer) {
                            Log.d(TAG, "Notify Decrypted if Private Note")

                            val zappedContent = it.split("\n").get(0)

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
