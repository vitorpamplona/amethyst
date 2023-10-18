package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.ExternalSignerUtils
import com.vitorpamplona.amethyst.service.SignerType
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentSetOf
import java.math.BigDecimal

class EventNotificationConsumer(private val applicationContext: Context) {
    suspend fun consume(event: GiftWrapEvent) {
        if (!LocalCache.justVerify(event)) return
        if (!notificationManager().areNotificationsEnabled()) return

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        LocalPreferences.allSavedAccounts().forEach {
            if (it.hasPrivKey || it.loggedInWithExternalSigner) {
                LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)?.let { acc ->
                    consumeIfMatchesAccount(event, acc)
                }
            }
        }
    }

    private suspend fun consumeIfMatchesAccount(pushWrappedEvent: GiftWrapEvent, account: Account) {
        val key = account.keyPair.privKey
        if (account.loginWithExternalSigner) {
            ExternalSignerUtils.account = account
            var cached = ExternalSignerUtils.cachedDecryptedContent[pushWrappedEvent.id]
            if (cached == null) {
                ExternalSignerUtils.decrypt(
                    pushWrappedEvent.content,
                    pushWrappedEvent.pubKey,
                    pushWrappedEvent.id,
                    SignerType.NIP44_DECRYPT
                )
                cached = ExternalSignerUtils.cachedDecryptedContent[pushWrappedEvent.id] ?: ""
            }
            pushWrappedEvent.unwrap(cached)?.let { notificationEvent ->
                if (!LocalCache.justVerify(notificationEvent)) return // invalid event
                if (LocalCache.notes[notificationEvent.id] != null) return // already processed

                LocalCache.justConsume(notificationEvent, null)

                unwrapAndConsume(notificationEvent, account)?.let { innerEvent ->
                    if (innerEvent is PrivateDmEvent) {
                        notify(innerEvent, account)
                    } else if (innerEvent is LnZapEvent) {
                        notify(innerEvent, account)
                    } else if (innerEvent is ChatMessageEvent) {
                        notify(innerEvent, account)
                    }
                }
            }
        } else if (key != null) {
            pushWrappedEvent.unwrap(key)?.let { notificationEvent ->
                LocalCache.justConsume(notificationEvent, null)

                unwrapAndConsume(notificationEvent, account)?.let { innerEvent ->
                    if (innerEvent is PrivateDmEvent) {
                        notify(innerEvent, account)
                    } else if (innerEvent is LnZapEvent) {
                        notify(innerEvent, account)
                    } else if (innerEvent is ChatMessageEvent) {
                        notify(innerEvent, account)
                    }
                }
            }
        }
    }

    private fun unwrapAndConsume(event: Event, account: Account): Event? {
        if (!LocalCache.justVerify(event)) return null

        return when (event) {
            is GiftWrapEvent -> {
                val key = account.keyPair.privKey
                if (key != null) {
                    event.cachedGift(key)?.let {
                        unwrapAndConsume(it, account)
                    }
                } else if (account.loginWithExternalSigner) {
                    var cached = ExternalSignerUtils.cachedDecryptedContent[event.id]
                    if (cached == null) {
                        ExternalSignerUtils.decrypt(
                            event.content,
                            event.pubKey,
                            event.id,
                            SignerType.NIP44_DECRYPT
                        )
                        cached = ExternalSignerUtils.cachedDecryptedContent[event.id] ?: ""
                    }
                    event.cachedGift(account.keyPair.pubKey, cached)?.let {
                        unwrapAndConsume(it, account)
                    }
                } else {
                    null
                }
            }
            is SealedGossipEvent -> {
                val key = account.keyPair.privKey
                if (key != null) {
                    event.cachedGossip(key)?.let {
                        // this is not verifiable
                        LocalCache.justConsume(it, null)
                        it
                    }
                } else if (account.loginWithExternalSigner) {
                    var cached = ExternalSignerUtils.cachedDecryptedContent[event.id]
                    if (cached == null) {
                        ExternalSignerUtils.decrypt(
                            event.content,
                            event.pubKey,
                            event.id,
                            SignerType.NIP44_DECRYPT
                        )
                        cached = ExternalSignerUtils.cachedDecryptedContent[event.id] ?: ""
                    }
                    event.cachedGossip(account.keyPair.pubKey, cached)?.let {
                        LocalCache.justConsume(it, null)
                        it
                    }
                } else {
                    null
                }
            }
            else -> {
                LocalCache.justConsume(event, null)
                event
            }
        }
    }

    private fun notify(event: ChatMessageEvent, acc: Account) {
        if (event.createdAt > TimeUtils.fiveMinutesAgo() && // old event being re-broadcasted
            event.pubKey != acc.userProfile().pubkeyHex
        ) { // from the user

            val chatNote = LocalCache.notes[event.id] ?: return
            val chatRoom = event.chatroomKey(acc.keyPair.pubKey.toHexKey())

            val followingKeySet = acc.followingKeySet()

            val isKnownRoom = (
                acc.userProfile().privateChatrooms[chatRoom]?.senderIntersects(followingKeySet) == true ||
                    acc.userProfile().hasSentMessagesTo(chatRoom)
                ) && !acc.isAllHidden(chatRoom.users)

            if (isKnownRoom) {
                val content = chatNote.event?.content() ?: ""
                val user = chatNote.author?.toBestDisplayName() ?: ""
                val userPicture = chatNote.author?.profilePicture()
                val noteUri = chatNote.toNEvent()
                notificationManager().sendDMNotification(
                    event.id,
                    content,
                    user,
                    userPicture,
                    noteUri,
                    applicationContext
                )
            }
        }
    }

    private fun notify(event: PrivateDmEvent, acc: Account) {
        val note = LocalCache.notes[event.id] ?: return

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fiveMinutesAgo()) return

        if (acc != null && acc.userProfile().pubkeyHex == event.verifiedRecipientPubKey()) {
            val followingKeySet = acc.followingKeySet()

            val knownChatrooms = acc.userProfile().privateChatrooms.keys.filter {
                (
                    acc.userProfile().privateChatrooms[it]?.senderIntersects(followingKeySet) == true ||
                        acc.userProfile().hasSentMessagesTo(it)
                    ) && !acc.isAllHidden(it.users)
            }.toSet()

            note.author?.let {
                if (ChatroomKey(persistentSetOf(it.pubkeyHex)) in knownChatrooms) {
                    val content = acc.decryptContent(note) ?: ""
                    val user = note.author?.toBestDisplayName() ?: ""
                    val userPicture = note.author?.profilePicture()
                    val noteUri = note.toNEvent()
                    notificationManager().sendDMNotification(event.id, content, user, userPicture, noteUri, applicationContext)
                }
            }
        }
    }

    private fun notify(event: LnZapEvent, acc: Account) {
        val noteZapEvent = LocalCache.notes[event.id] ?: return

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fiveMinutesAgo()) return

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val noteZapped = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) }

        if ((event.amount ?: BigDecimal.ZERO) < BigDecimal.TEN) return

        if (acc.userProfile().pubkeyHex == event.zappedAuthor().firstOrNull()) {
            val amount = showAmount(event.amount)
            val senderInfo = (noteZapRequest.event as? LnZapRequestEvent)?.let {
                val decryptedContent = acc.decryptZapContentAuthor(noteZapRequest)
                if (decryptedContent != null) {
                    val author = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    Pair(author, decryptedContent.content)
                } else if (!noteZapRequest.event?.content().isNullOrBlank()) {
                    Pair(noteZapRequest.author, noteZapRequest.event?.content())
                } else {
                    Pair(noteZapRequest.author, null)
                }
            }

            val zappedContent =
                noteZapped?.let { it1 -> acc.decryptContent(it1)?.split("\n")?.get(0) }

            val user = senderInfo?.first?.toBestDisplayName() ?: ""
            var title = applicationContext.getString(R.string.app_notification_zaps_channel_message, amount)
            senderInfo?.second?.ifBlank { null }?.let {
                title += " ($it)"
            }
            var content = applicationContext.getString(R.string.app_notification_zaps_channel_message_from, user)
            zappedContent?.let {
                content += " " + applicationContext.getString(R.string.app_notification_zaps_channel_message_for, zappedContent)
            }
            val userPicture = senderInfo?.first?.profilePicture()
            val noteUri = "nostr:Notifications"
            notificationManager().sendZapNotification(event.id, content, title, userPicture, noteUri, applicationContext)
        }
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}
