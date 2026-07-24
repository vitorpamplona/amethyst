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

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import android.os.SystemClock
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.amethyst.service.notifications.renderers.ArticleNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.BadgeNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.BuzzDmNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.ChessNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.CodeNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.DirectMessageNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.GroupMessageNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.MediaNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.MentionNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.ReactionNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.ReplyNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.RepostNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.ZapNotification
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
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
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EventNotificationConsumer"

/**
 * Turns a notification-relevant [Event] into a tray notification. Owns only the
 * *policy* — account matching, the shared suppress-when-foreground / don't-notify-
 * myself / muted-thread gates, and the routing of each kind to its renderer.
 * The *presentation* (title, body, style, accent, observability) lives in the
 * per-kind files under `renderers/`.
 */
class EventNotificationConsumer(
    private val applicationContext: Context,
    /** Reports how long each notification-processing wakelock was held (resource-usage ledger). */
    private val onWakeLockHeld: ((heldMs: Long) -> Unit)? = null,
) {
    companion object {
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val WAKEUP_WINDOW_MS = 30_000L
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
        val heldSince = SystemClock.elapsedRealtime()
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
        try {
            return block()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            onWakeLockHeld?.invoke(SystemClock.elapsedRealtime() - heldSince)
        }
    }

    /**
     * Entry point for notification-relevant events arriving into [LocalCache]
     * from any source (FCM push, UnifiedPush, Pokey, active relay subscriptions,
     * NotificationRelayService). Matches the event to a logged-in account by its
     * `p` tags and dispatches to [dispatchForAccount].
     */
    suspend fun consumeFromCache(event: Event) =
        withWakeLock {
            Log.d(TAG) { "New Notification from cache: kind=${event.kind} id=${event.id}" }

            if (!applicationContext.notificationManager().areNotificationsEnabled()) return@withWakeLock

            val matchingNote: Note? =
                if (event is WakeUpEvent) {
                    null
                } else {
                    LocalCache.getNoteIfExists(event) ?: return@withWakeLock
                }

            LocalPreferences.allSavedAccounts().forEach { savedAccount ->
                if (!savedAccount.hasPrivKey && !savedAccount.loggedInWithExternalSigner) return@forEach

                val accountHex = npubToHexOrNull(savedAccount.npub) ?: return@forEach
                if (matchingNote != null) {
                    val taggedOrPublicChatReply =
                        event.isTaggedUser(accountHex) ||
                            NotificationFeedFilter.isNotifiablePublicChatReply(matchingNote, accountHex)
                    if (!taggedOrPublicChatReply) return@forEach
                    if (!NotificationFeedFilter.tagsAnEventByUser(matchingNote, accountHex)) return@forEach
                }

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

    private fun npubToHexOrNull(npub: String): String? =
        runCatching { npub.bechToBytes("npub").toHexKey() }
            .onFailure { Log.d(TAG) { "Skipping non-decodable npub $npub: ${it.message}" } }
            .getOrNull()

    private suspend fun dispatchForAccount(
        event: Event,
        account: Account,
    ) {
        // Calls and wake-ups are high-priority and always notify, even when MainActivity is visible.
        when (event) {
            is CallOfferEvent -> {
                notifyIncomingCall(event, account)
                return
            }

            is WakeUpEvent -> {
                wakeUpFor(event, account)
                return
            }
        }

        // Everything else is suppressed while the user is actively on the home screen.
        if (MainActivity.isResumed) return

        // Don't push-notify events this account authored.
        if (event.pubKey == account.signer.pubKey) return

        // Drop reactions/zaps/reposts whose target note lives on a muted thread
        // (matches the in-app feed, which mutes all four).
        if (event is ReactionEvent || event is LnZapEvent || event is RepostEvent || event is GenericRepostEvent) {
            val target = LocalCache.getNoteIfExists(event)?.replyTo?.lastOrNull()
            if (target != null && account.isThreadMuted(account.resolveThreadRoot(target))) return
        }

        when (event) {
            is PrivateDmEvent -> DirectMessageNotification.notify(applicationContext, account, event)
            is ChatMessageEvent -> DirectMessageNotification.notify(applicationContext, account, event)
            is ChatMessageEncryptedFileHeaderEvent -> DirectMessageNotification.notify(applicationContext, account, event)

            // Buzz DM messages (participant-routed; the predicate already scoped
            // these kinds to Buzz DMs addressed to me).
            is StreamMessageV2Event -> BuzzDmNotification.notify(applicationContext, account, event)
            is ChatEvent -> BuzzDmNotification.notify(applicationContext, account, event)

            is LnZapEvent -> ZapNotification.notify(applicationContext, account, event)
            is NutzapEvent -> ZapNotification.notify(applicationContext, account, event)
            is OnchainZapEvent -> ZapNotification.notify(applicationContext, account, event)

            is ReactionEvent -> ReactionNotification.notify(applicationContext, account, event)

            is RepostEvent -> RepostNotification.notify(applicationContext, account, event)
            is GenericRepostEvent -> RepostNotification.notify(applicationContext, account, event)

            is BadgeAwardEvent -> BadgeNotification.notify(applicationContext, account, event)

            is TextNoteEvent -> notifyTextNote(event, account)
            is CommentEvent -> notifyComment(event, account)
            is ChannelMessageEvent -> notifyChannelMessage(event, account)

            is PictureEvent,
            is VideoNormalEvent,
            is VideoShortEvent,
            is VideoHorizontalEvent,
            is VideoVerticalEvent,
            -> MediaNotification.notify(applicationContext, account, event)

            is PollEvent -> MentionNotification.notify(applicationContext, account, event, titleRes = R.string.app_notification_poll_channel_message)

            is HighlightEvent,
            is LongTextNoteEvent,
            is WikiNoteEvent,
            -> ArticleNotification.notify(applicationContext, account, event)

            is GitIssueEvent -> CodeNotification.notify(applicationContext, account, event)
            is GitPatchEvent -> CodeNotification.notify(applicationContext, account, event)
            is GitPullRequestEvent -> CodeNotification.notify(applicationContext, account, event)
            is GitPullRequestUpdateEvent -> CodeNotification.notify(applicationContext, account, event)

            is LiveChessGameAcceptEvent -> ChessNotification.notify(applicationContext, account, event, R.string.app_notification_chess_challenge_accepted)
            is LiveChessMoveEvent -> ChessNotification.notify(applicationContext, account, event, R.string.app_notification_chess_your_turn)
        }
    }

    // Reply-vs-mention decisions are source-kind-specific, so they stay in the
    // dispatcher; the actual rendering is in ReplyNotification / MentionNotification.

    private suspend fun notifyTextNote(
        event: TextNoteEvent,
        account: Account,
    ) {
        val replyTargetId = event.replyingTo()
        if (replyTargetId != null) {
            val repliedNote = LocalCache.getNoteIfExists(replyTargetId)
            if (repliedNote?.author?.pubkeyHex == account.signer.pubKey) {
                val threadRoot = event.markedRoot()?.eventId ?: event.unmarkedRoot()?.eventId ?: replyTargetId
                ReplyNotification.notify(applicationContext, account, event, repliedNote, threadRoot)
                return
            }
        }
        MentionNotification.notify(applicationContext, account, event)
    }

    private suspend fun notifyComment(
        event: CommentEvent,
        account: Account,
    ) {
        val pubKey = account.signer.pubKey
        val isTarget = event.replyAuthorKeys().contains(pubKey) || event.rootAuthorKeys().contains(pubKey)
        if (!isTarget) return

        val parentNote = event.replyingTo()?.let { LocalCache.getNoteIfExists(it) }
        val threadRoot =
            event.rootEventIds().firstOrNull()
                ?: event.rootAddressIds().firstOrNull()
                ?: event.replyingToAddressOrEvent()
                ?: event.id

        ReplyNotification.notify(applicationContext, account, event, parentNote, threadRoot)
    }

    private suspend fun notifyChannelMessage(
        event: ChannelMessageEvent,
        account: Account,
    ) {
        val note = LocalCache.getNoteIfExists(event.id) ?: return

        if (NotificationFeedFilter.isNotifiablePublicChatReply(note, account.signer.pubKey)) {
            val parentNote = note.replyTo?.lastOrNull()
            val threadRoot = event.channelId() ?: event.id
            ReplyNotification.notify(applicationContext, account, event, parentNote, threadRoot)
        } else {
            MentionNotification.notify(applicationContext, account, event)
        }
    }

    // ---------------------------------------------------------------------
    // Directly-dispatched Marmot events (no `p` tag → routed by the caller)
    // ---------------------------------------------------------------------

    suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    ) = withWakeLock {
        GroupMessageNotification.notifyWelcome(applicationContext, account, event)
    }

    suspend fun notifyGroupMessage(
        innerEvent: ChatEvent,
        nostrGroupId: String,
        account: Account,
    ) = withWakeLock {
        GroupMessageNotification.notifyGroupMessage(applicationContext, account, innerEvent, nostrGroupId)
    }

    // ---------------------------------------------------------------------
    // Calls & wake-ups keep their bespoke, high-priority handling here.
    // ---------------------------------------------------------------------

    private suspend fun wakeUpFor(
        event: WakeUpEvent,
        account: Account,
    ) {
        val referencedTags = event.events().distinctBy { it.eventId }
        if (referencedTags.isEmpty()) {
            Log.d(TAG) { "WakeUp ${event.id} has no referenced events — skipping" }
            return
        }

        val referencedNotes = referencedTags.map { LocalCache.getOrCreateNote(it.eventId) }
        val authorCandidates =
            (event.authorKeys() + referencedTags.mapNotNull { it.author })
                .distinct()
                .ifEmpty { listOf(event.pubKey) }
                .map { LocalCache.getOrCreateUser(it) }

        coroutineScope {
            launch {
                try {
                    withTimeout(WAKEUP_WINDOW_MS) {
                        Amethyst.instance.relayProxyClientConnector.relayServices
                            .collect()
                    }
                } catch (_: TimeoutCancellationException) {
                    Log.d(TAG) { "WakeUp ${event.id} — ${WAKEUP_WINDOW_MS}ms relay window elapsed" }
                }
            }

            launch {
                val accountState = ScreenAuthAccount(account)
                val eventStates = referencedNotes.map { EventFinderQueryState(it, account) }
                val authorStates = authorCandidates.map { UserFinderQueryState(it, account) }

                try {
                    Amethyst.instance.authCoordinator.subscribe(accountState)
                    eventStates.forEach {
                        Amethyst.instance.sources.eventFinder
                            .subscribe(it)
                    }
                    authorStates.forEach {
                        Amethyst.instance.sources.userFinder
                            .subscribe(it)
                    }
                    delay(WAKEUP_WINDOW_MS)
                } finally {
                    Amethyst.instance.authCoordinator.unsubscribe(accountState)
                    eventStates.forEach {
                        Amethyst.instance.sources.eventFinder
                            .unsubscribe(it)
                    }
                    authorStates.forEach {
                        Amethyst.instance.sources.userFinder
                            .unsubscribe(it)
                    }
                }
            }
        }
    }

    private suspend fun notifyIncomingCall(
        event: CallOfferEvent,
        account: Account,
    ) {
        if (!account.isFollowing(event.pubKey)) return
        if (TimeUtils.now() - event.createdAt > CallManager.MAX_EVENT_AGE_SECONDS) return

        val callerUser = LocalCache.getOrCreateUser(event.pubKey)

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
                withContext(Dispatchers.IO) {
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
}
