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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

        // Kinds that can trigger notifications. GiftWrap and EphemeralGiftWrap carry
        // most encrypted payloads (DMs, Chess, CallOffer, Welcome); the rest arrive
        // unwrapped. SealedRumor (kind 13) is intentionally omitted — it only exists
        // inside a GiftWrap, and the wrapper drives the unwrap.
        private val NOTIFICATION_KINDS =
            listOf(
                GiftWrapEvent.KIND,
                EphemeralGiftWrapEvent.KIND,
                PrivateDmEvent.KIND,
                LnZapEvent.KIND,
                ReactionEvent.KIND,
                LiveChessGameAcceptEvent.KIND,
                LiveChessMoveEvent.KIND,
                WakeUpEvent.KIND,
            )
    }

    private val consumer = EventNotificationConsumer(context)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        Log.d(TAG, "Starting notification dispatcher")
        job =
            scope.launch {
                LocalCache
                    .observeNewEvents<Event>(Filter(kinds = NOTIFICATION_KINDS))
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

    fun stop() {
        job?.cancel()
        job = null
    }
}
