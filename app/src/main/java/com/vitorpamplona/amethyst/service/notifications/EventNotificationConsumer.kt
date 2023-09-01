package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
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
import kotlinx.collections.immutable.persistentSetOf
import java.math.BigDecimal

class EventNotificationConsumer(private val applicationContext: Context) {

    suspend fun consume(event: Event) {
        if (LocalCache.notes[event.id] == null) {
            if (LocalCache.justVerify(event)) {
                LocalCache.justConsume(event, null)

                val manager = notificationManager()
                if (manager.areNotificationsEnabled()) {
                    when (event) {
                        is PrivateDmEvent -> notify(event)
                        is LnZapEvent -> notify(event)
                        is GiftWrapEvent -> unwrapAndNotify(event)
                    }
                }
            }
        }
    }

    suspend fun unwrapAndConsume(event: Event, account: Account): Event? {
        if (!LocalCache.justVerify(event)) return null

        return when (event) {
            is GiftWrapEvent -> {
                val key = account.keyPair.privKey ?: return null
                event.cachedGift(key)?.let {
                    unwrapAndConsume(it, account)
                }
            }
            is SealedGossipEvent -> {
                val key = account.keyPair.privKey ?: return null
                event.cachedGossip(key)?.let {
                    // this is not verifiable
                    LocalCache.justConsume(it, null)
                    it
                }
            }
            else -> {
                LocalCache.justConsume(event, null)
                event
            }
        }
    }

    private suspend fun unwrapAndNotify(giftWrap: GiftWrapEvent) {
        val giftWrapNote = LocalCache.notes[giftWrap.id] ?: return

        LocalPreferences.allSavedAccounts().forEach {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)

            if (acc != null && acc.userProfile().pubkeyHex == giftWrap.recipientPubKey()) {
                val chatEvent = unwrapAndConsume(giftWrap, account = acc)

                if (chatEvent is ChatMessageEvent && acc.keyPair.privKey != null && chatEvent.pubKey != acc.userProfile().pubkeyHex) {
                    val chatNote = LocalCache.notes[chatEvent.id] ?: return
                    val chatRoom = chatEvent.chatroomKey(acc.keyPair.pubKey.toHexKey())

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
                        notificationManager().sendDMNotification(chatEvent.id, content, user, userPicture, noteUri, applicationContext)
                    }
                }
            }
        }
    }

    private fun notify(event: PrivateDmEvent) {
        val note = LocalCache.notes[event.id] ?: return

        LocalPreferences.allSavedAccounts().forEach {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)

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
    }

    private fun notify(event: LnZapEvent) {
        val noteZapEvent = LocalCache.notes[event.id] ?: return

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val noteZapped = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) }

        if ((event.amount ?: BigDecimal.ZERO) < BigDecimal.TEN) return

        LocalPreferences.allSavedAccounts().forEach {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)

            if (acc != null && acc.userProfile().pubkeyHex == event.zappedAuthor().firstOrNull()) {
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
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}
