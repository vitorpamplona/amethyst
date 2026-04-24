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
import android.os.PowerManager
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
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.InlineReplyTarget
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendChessNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendDMNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendMentionNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendReactionNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendReplyNotification
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.sendZapNotification
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
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
    companion object {
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
    }

    /**
     * Acquires a partial WakeLock during notification processing to ensure
     * the CPU stays awake long enough to decrypt, process, and dispatch
     * the notification, even in Doze mode.
     */
    private inline fun <T> withWakeLock(block: () -> T): T {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "amethyst:notification_processing",
            )
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
        try {
            return block()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    /**
     * Entry point for notification-relevant events arriving into [LocalCache]
     * from any source (FCM push, UnifiedPush, Pokey, active relay subscriptions,
     * NotificationRelayService). The [NotificationDispatcher] only invokes this
     * after [Account.newNotesPreProcessor] has fully unwrapped wraps and seals,
     * so this method receives the final inner payload directly.
     *
     * Matches the event to a logged-in account by its `p` tags and dispatches
     * to [dispatchForAccount].
     */
    suspend fun consumeFromCache(event: Event) =
        withWakeLock {
            Log.d(TAG) { "New Notification from cache: kind=${event.kind} id=${event.id}" }

            if (!notificationManager().areNotificationsEnabled()) return@withWakeLock

            val taggedNpubs =
                event
                    .taggedUserIds()
                    .mapTo(mutableSetOf()) { LocalCache.getOrCreateUser(it).pubkeyNpub() }
            if (taggedNpubs.isEmpty()) return@withWakeLock

            LocalPreferences.allSavedAccounts().forEach { savedAccount ->
                if (!savedAccount.hasPrivKey && !savedAccount.loggedInWithExternalSigner) return@forEach
                if (savedAccount.npub !in taggedNpubs) return@forEach

                val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(savedAccount.npub) ?: return@forEach
                try {
                    val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)
                    dispatchForAccount(event, account)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG) { "Failed to dispatch ${event.kind} ${event.id} for ${savedAccount.npub}: ${e.message}" }
                }
            }
        }

    private suspend fun dispatchForAccount(
        event: Event,
        account: Account,
    ) {
        // Calls and wake-ups are high-priority and always notify, even when MainActivity is visible.
        // They have their own freshness rules (CallManager.MAX_EVENT_AGE_SECONDS = 20s) and
        // author-identity semantics (caller pubkey is the other party), so they bypass the
        // shared gates below.
        when (event) {
            is CallOfferEvent -> {
                notifyIncomingCall(event, account)
                return
            }

            is WakeUpEvent -> {
                wakeUpFor(event, LocalCache.getOrCreateNote(event.id), account)
                return
            }
        }

        // Everything else is suppressed while the user is actively on the home screen.
        if (MainActivity.isResumed) return

        // Shared per-account gate: don't push-notify events this account authored.
        // Applied here (not at the observer) because in a multi-account session
        // account A's outgoing event legitimately becomes account B's incoming
        // notification on the same device. The observer already enforces the
        // 15-min rolling age window, so individual notify() methods don't need
        // to repeat either check.
        if (event.pubKey == account.signer.pubKey) return

        when (event) {
            is PrivateDmEvent -> notify(event, account)

            is LnZapEvent -> notify(event, account)

            is ChatMessageEvent -> notify(event, account)

            is ChatMessageEncryptedFileHeaderEvent -> notify(event, account)

            is ReactionEvent -> notify(event, account)

            is TextNoteEvent -> notify(event, account)

            is CommentEvent -> notify(event, account)

            is PictureEvent,
            is VideoNormalEvent,
            is VideoShortEvent,
            is VideoHorizontalEvent,
            is VideoVerticalEvent,
            is ChannelMessageEvent,
            is PollEvent,
            is GitPatchEvent,
            is GitIssueEvent,
            is HighlightEvent,
            is LongTextNoteEvent,
            is WikiNoteEvent,
            -> notifyMention(event, account)

            is LiveChessGameAcceptEvent -> notifyChessEvent(event, account, R.string.app_notification_chess_challenge_accepted)

            is LiveChessMoveEvent -> notifyChessEvent(event, account, R.string.app_notification_chess_your_turn)
            // WelcomeEvent is dispatched directly from processMarmotWelcomeFlow
            // (no `p` tag, so tag-based matching doesn't work).
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

    private suspend fun notify(
        event: ChatMessageEncryptedFileHeaderEvent,
        account: Account,
    ) {
        Log.d(TAG, "New ChatMessage File to Notify")
        // Age + self-author gates run centrally in dispatchForAccount.
        val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)
        val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
        val chatRoom = event.chatroomKey(account.signer.pubKey)

        val followingKeySet = account.followingKeySet()

        val isKnownRoom =
            chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true ||
                chatroomList.hasSentMessagesTo(chatRoom)

        if (!isKnownRoom) return

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

    private suspend fun notify(
        event: ChatMessageEvent,
        account: Account,
    ) {
        Log.d(TAG, "New ChatMessage to Notify")
        // Age + self-author gates run centrally in dispatchForAccount.
        val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)
        val chatNote = LocalCache.getNoteIfExists(event.id) ?: return
        val chatRoom = event.chatroomKey(account.signer.pubKey)

        val followingKeySet = account.followingKeySet()

        val isKnownRoom =
            chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true ||
                chatroomList.hasSentMessagesTo(chatRoom)

        if (!isKnownRoom) return

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

    private suspend fun notify(
        event: PrivateDmEvent,
        account: Account,
    ) {
        Log.d(TAG, "New Nip-04 DM to Notify")
        // Age + self-author gates run centrally in dispatchForAccount. The
        // dispatchForAccount self-check (event.pubKey != account.signer.pubKey)
        // also covers the "don't notify myself about DMs I sent" case that
        // was previously implicit via the recipient match below.
        if (account.signer.pubKey != event.verifiedRecipientPubKey()) return

        val note = LocalCache.getNoteIfExists(event.id) ?: return
        val chatroomList = LocalCache.getOrCreateChatroomList(account.signer.pubKey)

        val followingKeySet = account.followingKeySet()

        val chatRoom = event.chatroomKey(account.signer.pubKey)

        val isKnownRoom =
            chatroomList.rooms.get(chatRoom)?.senderIntersects(followingKeySet) == true ||
                chatroomList.hasSentMessagesTo(chatRoom)

        if (!isKnownRoom) return

        val author = note.author ?: return
        val content = decryptContent(note, account.signer) ?: return
        val user = author.toBestDisplayName()
        val userPicture = author.profilePicture()
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

    /**
     * Welcomes have no `p` tag, so [consumeFromCache]'s tag-based account match
     * can't route them. They are instead dispatched here directly by
     * [com.vitorpamplona.amethyst.ui.screen.loggedIn.processMarmotWelcomeFlow]
     * after [MarmotManager.processWelcome] joins the group — which is also the
     * only place we reliably know which account the invite was for.
     */
    suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    ) = withWakeLock {
        Log.d(TAG, "New Marmot Welcome to Notify")

        if (!notificationManager().areNotificationsEnabled()) return@withWakeLock
        if (MainActivity.isResumed) return@withWakeLock

        // old event being re-broadcast
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return@withWakeLock
        // a welcome we ourselves emitted
        if (event.pubKey == account.signer.pubKey) return@withWakeLock

        val nostrGroupId = event.nostrGroupId() ?: return@withWakeLock

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

        // Age + self-author gates run centrally in dispatchForAccount. For zaps
        // the self-check is effectively a no-op (receipts are signed by the LN
        // service, not the zapper) but the uniform rule is cheap and keeps the
        // downstream invariants simple.
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

        // Age + self-author gates run centrally in dispatchForAccount.
        // p-tag match already enforced by consumeFromCache; no redundant
        // isTaggedUser re-check needed.

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

    private suspend fun notify(
        event: TextNoteEvent,
        account: Account,
    ) {
        Log.d(TAG, "New TextNote to Notify")
        // Age + self-author gates run centrally in dispatchForAccount.

        val replyTargetId = event.replyingTo()

        if (replyTargetId != null) {
            val repliedNote = LocalCache.getNoteIfExists(replyTargetId)
            if (repliedNote?.author?.pubkeyHex == account.signer.pubKey) {
                val threadRoot = event.markedRoot()?.eventId ?: event.unmarkedRoot()?.eventId ?: replyTargetId
                notifyReply(event, account, repliedNote.event?.content, threadRoot)
                return
            }
        }

        // Not a reply to us but we're p-tagged — a mention or citation.
        notifyMention(event, account)
    }

    private suspend fun notify(
        event: CommentEvent,
        account: Account,
    ) {
        Log.d(TAG, "New NIP-22 Comment to Notify")
        // Age + self-author gates run centrally in dispatchForAccount.

        // NIP-22 marks direct-reply and root authors. Notify when the current
        // account is either (someone commenting on our post, or replying to our comment).
        val pubKey = account.signer.pubKey
        val isTarget = event.replyAuthorKeys().contains(pubKey) || event.rootAuthorKeys().contains(pubKey)
        if (!isTarget) return

        val parentContent =
            event
                .replyingTo()
                ?.let { LocalCache.getNoteIfExists(it)?.event?.content }

        val threadRoot =
            event.rootEventIds().firstOrNull()
                ?: event.rootAddressIds().firstOrNull()
                ?: event.replyingToAddressOrEvent()
                ?: event.id

        notifyReply(event, account, parentContent, threadRoot)
    }

    private suspend fun notifyReply(
        event: Event,
        account: Account,
        parentContent: String?,
        threadRootId: String,
    ) {
        val replyNote = LocalCache.getNoteIfExists(event.id) ?: return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val user = author.toBestDisplayName()
        val userPicture = author.profilePicture()

        val title = stringRes(applicationContext, R.string.app_notification_replies_channel_message, user)

        val replyExcerpt =
            event.content
                .split("\n")
                .firstOrNull { it.isNotBlank() }
                ?.take(280)
                ?: ""

        val parentExcerpt =
            parentContent
                ?.split("\n")
                ?.firstOrNull { it.isNotBlank() }
                ?.take(140)

        val content =
            if (!parentExcerpt.isNullOrBlank()) {
                replyExcerpt + "\n\n" +
                    stringRes(
                        applicationContext,
                        R.string.app_notification_replies_channel_message_for,
                        parentExcerpt,
                    )
            } else {
                replyExcerpt
            }

        val accountNpub =
            account.signer.pubKey
                .hexToByteArray()
                .toNpub()
        val noteUri = replyNote.toNEvent() + ACCOUNT_QUERY_PARAM + accountNpub

        notificationManager()
            .sendReplyNotification(
                id = event.id,
                messageBody = content,
                messageTitle = title,
                time = event.createdAt,
                pictureUrl = userPicture,
                uri = noteUri,
                applicationContext = applicationContext,
                threadRootId = threadRootId,
                inlineReply = InlineReplyTarget(accountNpub = accountNpub, targetEventId = event.id),
            )
    }

    private suspend fun notifyMention(
        event: Event,
        account: Account,
    ) {
        // Age + self-author gates run centrally in dispatchForAccount.
        val note = LocalCache.getNoteIfExists(event.id) ?: return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val user = author.toBestDisplayName()
        val userPicture = author.profilePicture()

        val title = stringRes(applicationContext, R.string.app_notification_mentions_channel_message, user)

        val content =
            event.content
                .split("\n")
                .firstOrNull { it.isNotBlank() }
                ?.take(280)
                ?: ""

        val accountNpub =
            account.signer.pubKey
                .hexToByteArray()
                .toNpub()
        val noteUri = note.toNEvent() + ACCOUNT_QUERY_PARAM + accountNpub

        notificationManager()
            .sendMentionNotification(
                id = event.id,
                messageBody = content,
                messageTitle = title,
                time = event.createdAt,
                pictureUrl = userPicture,
                uri = noteUri,
                applicationContext = applicationContext,
            )
    }

    private suspend fun notifyChessEvent(
        event: BaseChessEvent,
        account: Account,
        contentStringRes: Int,
    ) {
        // Age + self-author gates run centrally in dispatchForAccount.
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
