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
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Observes [LocalCache] for notification-relevant events and hands them to
 * [EventNotificationConsumer]. Events can reach [LocalCache] from any source —
 * FCM push, UnifiedPush, Pokey, active relay subscriptions, or the
 * [NotificationRelayService] — and the observer fires once per new insertion,
 * giving us a single dedup'd source of truth for notification triggers.
 *
 * The dispatcher itself is always on (any of the delivery paths can fire it).
 * Foreground suppression and per-event filtering are handled downstream in
 * [EventNotificationConsumer].
 */
class NotificationDispatcher(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "NotificationDispatcher"

        // The dispatcher observes the *final* notification payload kinds.
        // GiftWrap/EphemeralGiftWrap (1059/21059) and SealedRumor (13) are NOT
        // listed here — by the time we care, Account.newNotesPreProcessor has
        // already unwrapped them and inserted the inner payload into LocalCache,
        // which fires the observer a second time on the inner event.
        //
        // WelcomeEvent (kind:444) is also excluded: it has no `p` tag, so
        // consumeFromCache can't route it. It's delivered directly via
        // [notifyWelcome] from processMarmotWelcomeFlow, which does know the
        // recipient account.
        private val NOTIFICATION_KINDS: Set<Int> =
            setOf(
                // Direct-arrival
                PrivateDmEvent.KIND,
                LnZapEvent.KIND,
                ReactionEvent.KIND,
                TextNoteEvent.KIND,
                CommentEvent.KIND,
                // Public content kinds — routed to the Mentions channel when p-tagged.
                PictureEvent.KIND,
                VideoNormalEvent.KIND,
                VideoShortEvent.KIND,
                VideoHorizontalEvent.KIND,
                VideoVerticalEvent.KIND,
                ChannelMessageEvent.KIND,
                PollEvent.KIND,
                GitPatchEvent.KIND,
                GitIssueEvent.KIND,
                HighlightEvent.KIND,
                LongTextNoteEvent.KIND,
                WikiNoteEvent.KIND,
                LiveChessGameAcceptEvent.KIND,
                LiveChessMoveEvent.KIND,
                WakeUpEvent.KIND,
                // Unwrapped from GiftWrap → Seal
                ChatMessageEvent.KIND,
                ChatMessageEncryptedFileHeaderEvent.KIND,
                // Unwrapped from EphemeralGiftWrap
                CallOfferEvent.KIND,
            )
    }

    private val consumer = EventNotificationConsumer(context)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        Log.d(TAG, "Starting notification dispatcher")

        // Only fire on events created after the dispatcher starts — equivalent
        // to the relay protocol's `limit: 0` subscription semantics, so we
        // don't retrigger on historical re-broadcasts of events the user has
        // already seen. Captured once and shared across filter rebuilds
        // triggered by account changes.
        val dispatcherSince = TimeUtils.now()

        job =
            scope.launch {
                // Ensure the saved-accounts StateFlow is primed from disk.
                // accountsFlow() exposes the backing MutableStateFlow which
                // starts as null and only populates on the first suspend read.
                LocalPreferences.allSavedAccounts()

                LocalPreferences
                    .accountsFlow()
                    .filterNotNull()
                    .map { accounts ->
                        accounts
                            .filter { it.hasPrivKey || it.loggedInWithExternalSigner }
                            .mapNotNullTo(mutableSetOf()) { npubToHexOrNull(it.npub) }
                    }.distinctUntilChanged()
                    .collectLatest { pubkeys ->
                        if (pubkeys.isEmpty()) {
                            Log.d(TAG) { "No notifiable accounts; observer idle." }
                            return@collectLatest
                        }

                        Log.d(TAG) { "Observing notifications for ${pubkeys.size} account(s)." }

                        // Single observer predicate. Each check is cheap and
                        // short-circuits so kind mismatch (by far the most
                        // common case) rejects before any allocation.
                        //
                        // - kind ∈ NOTIFICATION_KINDS    — channel-relevant types
                        // - createdAt ≥ dispatcherSince  — `limit: 0` semantics,
                        //   drops re-broadcasts from before this session
                        // - createdAt ≥ fifteenMinutesAgo — rolling freshness,
                        //   matches the downstream per-channel policy. Calls
                        //   use a stricter 20s check in notifyIncomingCall so
                        //   they still pass through.
                        // - event.notifies(pubkey) for any of our accounts —
                        //   each kind decides which tag(s) name its recipients
                        //   (lowercase `p` by default, plus uppercase `P` for
                        //   NIP-22 root authors, etc.).
                        val predicate = { event: Event ->
                            event.kind in NOTIFICATION_KINDS &&
                                event.createdAt >= dispatcherSince &&
                                event.createdAt >= TimeUtils.fifteenMinutesAgo() &&
                                pubkeys.any { event.notifies(it) }
                        }

                        LocalCache
                            .observeNewEvents<Event>(predicate)
                            .collect { event ->
                                try {
                                    consumer.consumeFromCache(event)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(TAG, "Failed to dispatch notification for ${event.kind} ${event.id}", e)
                                }
                            }
                    }
            }
    }

    private fun npubToHexOrNull(npub: String): String? =
        runCatching { npub.bechToBytes("npub").toHexKey() }
            .onFailure { Log.d(TAG) { "Skipping non-decodable npub $npub: ${it.message}" } }
            .getOrNull()

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Direct-invocation entry point for [WelcomeEvent]. Bypasses the
     * cache-observer path because Welcomes have no `p` tag for account
     * routing. Called from processMarmotWelcomeFlow once MLS group join
     * succeeds — at which point we know which account the invite was for.
     */
    suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    ) {
        try {
            consumer.notifyWelcome(event, account)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to dispatch Welcome notification ${event.id}", e)
        }
    }
}
