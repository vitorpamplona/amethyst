package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.ui.note.showAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EventNotificationConsumer(private val applicationContext: Context) {
    fun consume(event: Event) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            if (LocalCache.notes[event.id] == null) {
                // adds to database
                LocalCache.verifyAndConsume(event, null)

                val manager = notificationManager()
                if (manager.areNotificationsEnabled()) {
                    when (event) {
                        is PrivateDmEvent -> notify(event)
                        is LnZapEvent -> notify(event)
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

                val messagingWith = acc.userProfile().privateChatrooms.keys.filter {
                    (
                        it.pubkeyHex in followingKeySet || acc.userProfile()
                            .hasSentMessagesTo(it)
                        ) && !acc.isHidden(it)
                }.toSet()

                if (note.author in messagingWith) {
                    val content = acc.decryptContent(note) ?: ""
                    val user = note.author?.toBestDisplayName() ?: ""
                    val userPicture = note.author?.profilePicture()
                    val noteUri = note.toNEvent()
                    notificationManager().sendDMNotification(event.id, content, user, userPicture, noteUri, applicationContext)
                }
            }
        }
    }

    private fun notify(event: LnZapEvent) {
        val noteZapEvent = LocalCache.notes[event.id] ?: return

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) }
        val noteZapped = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) }

        LocalPreferences.allSavedAccounts().forEach {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)

            if (acc != null && acc.userProfile().pubkeyHex == event.zappedAuthor().firstOrNull()) {
                val amount = showAmount(event.amount)
                val senderInfo = (noteZapRequest?.event as? LnZapRequestEvent)?.let {
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

    private fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}
