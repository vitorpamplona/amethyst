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
package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendChessNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendReactionNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EventNotificationConsumer"
private const val ACCOUNT_QUERY_PARAM = "?account="
private const val SCROLL_TO_QUERY_PARAM = "&scrollTo="

class EventNotificationConsumer(
    private val applicationContext: Context,
) {
    suspend fun consume(event: GiftWrapEvent) {
        Log.d(TAG, "New Notification Arrived")

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        var matchAccount = false
        LocalPreferences.allSavedAccounts().forEach {
            if (!matchAccount && (it.hasPrivKey || it.loggedInWithExternalSigner)) {
                LocalPreferences.loadAccountConfigFromEncryptedStorage(it.npub)?.let { acc ->
                    Log.d(TAG) { "New Notification Testing if for ${it.npub}" }
                    try {
                        val account = Amethyst.instance.accountsCache.loadAccount(acc)
                        consumeIfMatchesAccount(event, account)
                        matchAccount = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG) { "Message was not for user ${it.npub}: ${e.message}" }
                    }
                }
            }
        }
    }

    private suspend fun consumeIfMatchesAccount(
        pushWrappedEvent: GiftWrapEvent,
        account: Account,
    ) {
        val notificationEvent = pushWrappedEvent.unwrapThrowing(account.signer)
        consumeNotificationEvent(notificationEvent, account)
    }

    suspend fun consumeNotificationEvent(
        notificationEvent: Event,
        account: Account,
    ) {
        Log.d(TAG) { "New Notification ${notificationEvent.kind} ${notificationEvent.id} Arrived for ${account.signer.pubKey}" }
        val consumed = LocalCache.hasConsumed(notificationEvent)
        Log.d(TAG) { "New Notification ${notificationEvent.kind} ${notificationEvent.id} Arrived for ${account.signer.pubKey} consumed= $consumed" }
        if (!consumed) {
            Log.d(TAG, "New Notification was verified")
            if (!notificationManager().areNotificationsEnabled()) return
            Log.d(TAG, "Notifications are enabled")

            unwrapAndConsume(notificationEvent, account.signer)?.let { innerNote ->
                val innerEvent = innerNote.event
                Log.d(TAG) { "Unwrapped consume ${innerEvent?.javaClass?.simpleName}" }

                when (innerEvent) {
                    is PrivateDmEvent -> {
                        notify(innerEvent, account)
                    }

                    is LnZapEvent -> {
                        notify(innerEvent, account)
                    }

                    is ChatMessageEvent -> {
                        notify(innerEvent, account)
                    }

                    is ChatMessageEncryptedFileHeaderEvent -> {
                        notify(innerEvent, account)
                    }

                    is ReactionEvent -> {
                        notify(innerEvent, account)
                    }

                    is LiveChessGameAcceptEvent -> {
                        notifyChessEvent(innerEvent, account, R.string.app_notification_chess_challenge_accepted)
                    }

                    is LiveChessMoveEvent -> {
                        notifyChessEvent(innerEvent, account, R.string.app_notification_chess_your_turn)
                    }

                    is CallOfferEvent -> {
                        notifyIncomingCall(innerEvent, account)
                    }

                    is WakeUpEvent -> {
                        wakeUpFor(innerEvent, innerNote, account)
                    }

                    is WelcomeEvent -> {
                        notify(innerEvent, account)
                    }
                }
            }
        }
    }

    suspend fun wakeUpFor(
        event: WakeUpEvent,
        note: Note,
        account: Account,
    ) {
        coroutineScope {
            // keeps the relay connection active for 30 seconds.
            launch {
                try {
                    withTimeout(30_000L) {
                        Amethyst.instance.relayProxyClientConnector.relayServices
                            .collect()
                    }
                } catch (_: TimeoutCancellationException) {
                }
            }

            // keeps the subscription to download this event active for 30 seconds.
            launch {
                val accountState = ScreenAuthAccount(account)
                val eventState = EventFinderQueryState(note, account)
                val authorState = UserFinderQueryState(note.author ?: LocalCache.getOrCreateUser(event.pubKey), account)

                try {
                    Amethyst.instance.authCoordinator.subscribe(accountState)
                    Amethyst.instance.sources.eventFinder
                        .subscribe(eventState)
                    Amethyst.instance.sources.userFinder
                        .subscribe(authorState)
                    delay(30_000)
                } finally {
                    Amethyst.instance.authCoordinator.unsubscribe(accountState)
                    Amethyst.instance.sources.eventFinder
                        .unsubscribe(eventState)
                    Amethyst.instance.sources.userFinder
                        .unsubscribe(authorState)
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
                LocalPreferences.loadAccountConfigFromEncryptedStorage(it.npub)?.let { accountSettings ->
                    Log.d(TAG) { "New Notification Testing if for ${it.npub}" }
                    try {
                        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)
                        consumeNotificationEvent(event, account)
                        matchAccount = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG) { "Message was not for user ${it.npub}: ${e.message}" }
                    }
                }
            }
        }
    }

    private suspend fun unwrapAndConsume(
        event: Event,
        signer: NostrSigner,
    ): Note? {
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

                    val note = LocalCache.getOrCreateNote(inner.id)
                    // this is not verifiable
                    if (LocalCache.justConsume(inner, null, true)) {
                        note
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            else -> {
                val note = LocalCache.getOrCreateNote(event.id)
                LocalCache.justConsume(event, null, false)
                note
            }
        }
    }

    private suspend fun notify(
        event: ChatMessageEncryptedFileHeaderEvent,
        account: Account,
    ) {
        Log.d(TAG, "New ChatMessage File to Notify")
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != account.signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(account.signer.pubKey)

            val followingKeySet = account.followingKeySet()

            val isKnownRoom =
                (
                    chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)
                )

            if (isKnownRoom) {
                val content = chatNote.event?.content ?: ""
                val user = chatNote.author?.toBestDisplayName() ?: ""
                val userPicture = chatNote.author?.profilePicture()
                val accountNpub =
                    account.signer.pubKey
                        .hexToByteArray()
                        .toNpub()
                val chatroomMembers = chatRoom.users.joinToString(",")
                val noteUri = chatNote.toNEvent() + ACCOUNT_QUERY_PARAM + accountNpub

                notificationManager()
                    .sendDMNotification(
                        event.id,
                        content,
                        user,
                        event.createdAt,
                        userPicture,
                        noteUri,
                        applicationContext,
                        accountNpub = accountNpub,
                        accountPictureUrl = account.userProfile().profilePicture(),
                        chatroomMembers = chatroomMembers,
                    )
            }
        }
    }

    private suspend fun notify(
        event: ChatMessageEvent,
        account: Account,
    ) {
        Log.d(TAG, "New ChatMessage to Notify")
        if (
            // old event being re-broadcasted
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            // don't display if it comes from me.
            event.pubKey != account.signer.pubKey
        ) { // from the user
            Log.d(TAG, "Notifying")
            val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)
            val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
            val chatRoom = event.chatroomKey(account.signer.pubKey)

            val followingKeySet = account.followingKeySet()

            val isKnownRoom = chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)

            if (isKnownRoom) {
                val content = chatNote.event?.content ?: ""
                val user = chatNote.author?.toBestDisplayName() ?: ""
                val userPicture = chatNote.author?.profilePicture()
                val accountNpub =
                    account.signer.pubKey
                        .hexToByteArray()
                        .toNpub()
                val chatroomMembers = chatRoom.users.joinToString(",")
                val noteUri = chatNote.toNEvent() + ACCOUNT_QUERY_PARAM + accountNpub

                notificationManager()
                    .sendDMNotification(
                        id = event.id,
                        messageBody = content,
                        senderName = user,
                        time = event.createdAt,
                        pictureUrl = userPicture,
                        uri = noteUri,
                        applicationContext = applicationContext,
                        accountNpub = accountNpub,
                        accountPictureUrl = account.userProfile().profilePicture(),
                        chatroomMembers = chatroomMembers,
                    )
            }
        }
    }

    private suspend fun notify(
        event: PrivateDmEvent,
        account: Account,
    ) {
        Log.d(TAG, "New Nip-04 DM to Notify")
        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        if (account.signer.pubKey == event.verifiedRecipientPubKey()) {
            val note = LocalCache.getNoteIfExists(event.id) ?: return
            val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)

            val followingKeySet = account.followingKeySet()

            val chatRoom = event.chatroomKey(account.signer.pubKey)

            val isKnownRoom = chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true || chatroomList.hasSentMessagesTo(chatRoom)

            if (isKnownRoom) {
                note.author?.let {
                    decryptContent(note, account.signer)?.let { content ->
                        val user = note.author?.toBestDisplayName() ?: ""
                        val userPicture = note.author?.profilePicture()
                        val accountNpub =
                            account.signer.pubKey
                                .hexToByteArray()
                                .toNpub()
                        val noteUri = note.toNEvent() + ACCOUNT_QUERY_PARAM + accountNpub

                        notificationManager()
                            .sendDMNotification(
                                id = event.id,
                                messageBody = content,
                                senderName = user,
                                time = event.createdAt,
                                pictureUrl = userPicture,
                                uri = noteUri,
                                applicationContext = applicationContext,
                                accountNpub = accountNpub,
                                accountPictureUrl = account.userProfile().profilePicture(),
                                chatroomMembers = null,
                            )
                    }
                }
            }
        }
    }

    private suspend fun notify(
        event: WelcomeEvent,
        account: Account,
    ) {
        Log.d(TAG, "New Marmot Welcome to Notify")

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return
        // a welcome we ourselves emitted
        if (event.pubKey == account.signer.pubKey) return

        val nostrGroupId = event.nostrGroupId() ?: return
        val manager = account.marmotManager ?: return

        // Best-effort: process the welcome here so the chatroom is hydrated
        // before composing the notification body. The push-notification
        // background path does NOT go through Account.eventProcessor, so
        // without this the invitee would only join the group later, when
        // they next open the app and the relay subscription redelivers.
        if (!manager.isMember(nostrGroupId)) {
            try {
                val result = manager.processWelcome(event, nostrGroupId)
                if (result is WelcomeResult.Joined) {
                    val chatroom = account.marmotGroupList.getOrCreateGroup(result.nostrGroupId)
                    manager.syncMetadataTo(result.nostrGroupId, chatroom)
                    account.marmotGroupList.notifyGroupChanged(result.nostrGroupId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG) { "Failed to process Marmot Welcome from notification path: ${e.message}" }
            }
        }

        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        val groupName = chatroom.displayName.value?.takeIf { it.isNotBlank() } ?: "a private group"
        val inviter = LocalCache.getOrCreateUser(event.pubKey)
        val inviterName = inviter.toBestDisplayName()
        val inviterPicture = inviter.profilePicture()

        val accountNpub =
            account.signer.pubKey
                .hexToByteArray()
                .toNpub()
        // marmot:<groupHex>?account=<npub> — parsed by uriToRoute below.
        val noteUri = "marmot:$nostrGroupId$ACCOUNT_QUERY_PARAM$accountNpub"

        notificationManager()
            .sendDMNotification(
                id = event.id,
                messageBody = "You've been added to $groupName",
                senderName = inviterName,
                time = event.createdAt,
                pictureUrl = inviterPicture,
                uri = noteUri,
                applicationContext = applicationContext,
                accountNpub = accountNpub,
                accountPictureUrl = account.userProfile().profilePicture(),
                chatroomMembers = null,
            )
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
        return when (event) {
            is PrivateDmEvent -> {
                event.decryptContent(signer)
            }

            is LnZapRequestEvent -> {
                decryptZapContentAuthor(event, signer)?.content
            }

            else -> {
                event?.content
            }
        }
    }

    private suspend fun notify(
        event: LnZapEvent,
        account: Account,
    ) {
        Log.d(TAG, "New Zap to Notify")
        Log.d(TAG) { "Notify Start ${event.toNostrUri()}" }
        LocalCache.getNoteIfExists(event.id) ?: return

        Log.d(TAG, "Notify Not Notified Yet")

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        Log.d(TAG, "Notify Not an old event")

        val noteZapRequest = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return
        val noteZapped = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return

        Log.d(TAG) { "Notify ZapRequest $noteZapRequest zapped $noteZapped" }

        if ((event.amount ?: BigDecimal.ZERO) < BigDecimal.TEN) return

        Log.d(TAG, "Notify Amount Bigger than 10")

        if (event.isTaggedUser(account.signer.pubKey)) {
            val amount = showAmount(event.amount)

            Log.d(TAG) { "Notify Amount $amount" }

            (noteZapRequest.event as? LnZapRequestEvent)?.let { event ->
                decryptZapContentAuthor(event, account.signer)?.let { decryptedEvent ->
                    Log.d(TAG) { "Notify Decrypted if Private Zap ${event.id}" }

                    val author = LocalCache.getOrCreateUser(decryptedEvent.pubKey)
                    val senderInfo = Pair(author, decryptedEvent.content.ifBlank { null })

                    if (noteZapped.event?.content != null) {
                        decryptContent(noteZapped, account.signer)?.let { decrypted ->
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
                            val noteUri =
                                "notifications$ACCOUNT_QUERY_PARAM" +
                                    account.signer.pubKey
                                        .hexToByteArray()
                                        .toNpub() +
                                    SCROLL_TO_QUERY_PARAM + event.id

                            Log.d(TAG) { "Notify ${event.id} $content $title $noteUri" }

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
                        val noteUri =
                            "notifications$ACCOUNT_QUERY_PARAM" +
                                account.signer.pubKey
                                    .hexToByteArray()
                                    .toNpub() +
                                SCROLL_TO_QUERY_PARAM + event.id

                        Log.d(TAG) { "Notify ${event.id} $title $noteUri" }

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

    private suspend fun notify(
        event: ReactionEvent,
        account: Account,
    ) {
        Log.d(TAG, "New Reaction to Notify")

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return

        // don't notify for own reactions
        if (event.pubKey == account.signer.pubKey) return

        // only notify if the reaction is for the current user
        if (!event.isTaggedUser(account.signer.pubKey)) return

        val reactedPostId = event.originalPost().firstOrNull() ?: return
        val reactedNote = LocalCache.checkGetOrCreateNote(reactedPostId)

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val user = author.toBestDisplayName()
        val userPicture = author.profilePicture()

        val reactionContent = event.content
        val reactionSymbol =
            when {
                reactionContent == ReactionEvent.LIKE || reactionContent.isBlank() -> "\uD83E\uDD19"
                reactionContent == ReactionEvent.DISLIKE -> "\uD83D\uDC4E"
                else -> reactionContent
            }

        val title = "$reactionSymbol $user"

        val reactedContent =
            reactedNote
                ?.event
                ?.content
                ?.split("\n")
                ?.firstOrNull() ?: ""

        val content =
            if (reactedContent.isNotBlank()) {
                stringRes(
                    applicationContext,
                    R.string.app_notification_reactions_channel_message_for,
                    reactedContent,
                )
            } else {
                stringRes(
                    applicationContext,
                    R.string.app_notification_reactions_channel_message,
                    user,
                )
            }

        val noteUri =
            "notifications$ACCOUNT_QUERY_PARAM" +
                account.signer.pubKey
                    .hexToByteArray()
                    .toNpub() +
                SCROLL_TO_QUERY_PARAM + event.id

        notificationManager()
            .sendReactionNotification(
                event.id,
                content,
                title,
                event.createdAt,
                userPicture,
                noteUri,
                applicationContext,
            )
    }

    private suspend fun notifyChessEvent(
        event: BaseChessEvent,
        account: Account,
        contentStringRes: Int,
    ) {
        if (
            event.createdAt > TimeUtils.fifteenMinutesAgo() &&
            event.pubKey != account.signer.pubKey
        ) {
            val author = LocalCache.getOrCreateUser(event.pubKey)
            val user = author.toBestDisplayName()
            val userPicture = author.profilePicture()
            val title = stringRes(applicationContext, R.string.app_notification_chess_channel_name)
            val content = stringRes(applicationContext, contentStringRes, user)
            val noteUri =
                "notifications$ACCOUNT_QUERY_PARAM" +
                    account.signer.pubKey
                        .hexToByteArray()
                        .toNpub() +
                    SCROLL_TO_QUERY_PARAM + event.id

            notificationManager()
                .sendChessNotification(
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

    private suspend fun notifyIncomingCall(
        event: CallOfferEvent,
        account: Account,
    ) {
        if (!account.isFollowing(event.pubKey)) return

        if (TimeUtils.now() - event.createdAt > CallManager.MAX_EVENT_AGE_SECONDS) return

        val callerUser = LocalCache.getOrCreateUser(event.pubKey)

        // If the caller's metadata hasn't been loaded yet (e.g. fresh process from
        // a push notification), briefly subscribe to the user finder so we can
        // resolve the user's display name instead of showing the raw pubkey.
        if (callerUser.metadataOrNull()?.bestName() == null) {
            val authorState = UserFinderQueryState(callerUser, account)
            try {
                Amethyst.instance.sources.userFinder
                    .subscribe(authorState)
                withTimeoutOrNull(5_000L) {
                    callerUser
                        .metadata()
                        .flow
                        .first { it?.info?.bestName() != null }
                }
            } finally {
                Amethyst.instance.sources.userFinder
                    .unsubscribe(authorState)
            }
        }

        val callerName = callerUser.toBestDisplayName()

        val callerBitmap =
            callerUser.profilePicture()?.let { pictureUrl ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val request =
                            ImageRequest
                                .Builder(applicationContext)
                                .data(pictureUrl)
                                .allowHardware(false)
                                .build()
                        val result = ImageLoader(applicationContext).execute(request)
                        (result.image?.asDrawable(applicationContext.resources) as? BitmapDrawable)?.bitmap
                    } catch (_: Exception) {
                        null
                    }
                }
            }

        CallNotifier.send(
            callerName = callerName,
            callerBitmap = callerBitmap,
            applicationContext = applicationContext,
        )
    }

    fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            as NotificationManager
}
