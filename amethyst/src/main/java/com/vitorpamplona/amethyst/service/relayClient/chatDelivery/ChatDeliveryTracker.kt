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
package com.vitorpamplona.amethyst.service.relayClient.chatDelivery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayInsertConfirmationCollector
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Delivery progress of one recipient's gift wrap (NIP-17 DMs). */
@Immutable
data class RecipientDelivery(
    val recipient: HexKey,
    val targetRelays: Set<NormalizedRelayUrl>,
    val acceptedRelays: Set<NormalizedRelayUrl> = emptySet(),
) {
    val isDelivered: Boolean
        get() = acceptedRelays.isNotEmpty()
}

/**
 * Delivery progress of one outgoing chat message. For DMs [recipients] carries one
 * entry per gift wrap (the sender's self-copy included); for public rooms it is
 * null and [targetRelays]/[acceptedRelays] describe the room's relay set.
 */
@Immutable
data class ChatDelivery(
    val targetRelays: Set<NormalizedRelayUrl>,
    val acceptedRelays: Set<NormalizedRelayUrl> = emptySet(),
    val recipients: List<RecipientDelivery>? = null,
) {
    val isFullyAccepted: Boolean
        get() =
            if (recipients != null) {
                recipients.all { it.isDelivered }
            } else {
                targetRelays.isNotEmpty() && acceptedRelays.containsAll(targetRelays)
            }
}

/**
 * Remembers, per outgoing chat message, which relays were targeted at publish time
 * and which have accepted (relay OK) since — the source for the delivery ticks on
 * own chat bubbles.
 *
 * The relay pool's outbox drops its entry once an event is fully acked, and relay
 * OKs for recipient gift wraps aggregate onto a single aliased Note, losing WHICH
 * recipient's wrap was accepted. This tracker fills both gaps: sends register the
 * `displayed note id -> (recipient, wrap id, target relays)` mapping while it still
 * exists (inside the publish loop), and a persistent OK listener attributes each
 * acceptance back to the message and, for DMs, to the recipient.
 *
 * In-memory only: history from before an app restart simply has no entry, and the
 * UI falls back to the Note's seen-on relays.
 */
class ChatDeliveryTracker(
    client: INostrClient,
) {
    private val lock = Any()

    private val deliveries = MutableStateFlow(mapOf<HexKey, ChatDelivery>())

    // wrap id -> (displayed note id, recipient pubkey)
    private var wrapIndex = mapOf<HexKey, Pair<HexKey, HexKey>>()

    // insertion order of displayed note ids, for pruning
    private val trackedOrder = ArrayDeque<HexKey>()

    @Suppress("unused")
    private val okCollector =
        RelayInsertConfirmationCollector(client) { eventId, relay ->
            onAccepted(eventId, relay.url)
        }

    /** Registers a public room message published to the room's [targetRelays]. */
    fun trackPublic(
        eventId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        if (targetRelays.isEmpty()) return
        synchronized(lock) {
            if (deliveries.value[eventId] == null) {
                registerNoteId(eventId)
            }
            deliveries.value = deliveries.value + (eventId to ChatDelivery(targetRelays))
        }
    }

    /**
     * Registers one recipient's gift wrap of the DM whose chat feed shows
     * [displayedNoteId] (the inner rumor's id).
     */
    fun trackWrap(
        displayedNoteId: HexKey,
        recipient: HexKey,
        wrapId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        synchronized(lock) {
            val current = deliveries.value[displayedNoteId]
            if (current == null) {
                registerNoteId(displayedNoteId)
            }

            val recipients = (current?.recipients ?: emptyList()) + RecipientDelivery(recipient, targetRelays)

            deliveries.value =
                deliveries.value +
                (
                    displayedNoteId to
                        ChatDelivery(
                            targetRelays = (current?.targetRelays ?: emptySet()) + targetRelays,
                            acceptedRelays = current?.acceptedRelays ?: emptySet(),
                            recipients = recipients,
                        )
                )

            wrapIndex = wrapIndex + (wrapId to (displayedNoteId to recipient))
        }
    }

    fun deliveryFlow(noteId: HexKey): Flow<ChatDelivery?> = deliveries.map { it[noteId] }.distinctUntilChanged()

    fun currentFor(noteId: HexKey): ChatDelivery? = deliveries.value[noteId]

    private fun onAccepted(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        // Cheap negative path: OKs fire for every event the app publishes anywhere.
        val isWrap = wrapIndex[eventId]
        if (isWrap == null && deliveries.value[eventId] == null) return

        synchronized(lock) {
            val wrapTarget = wrapIndex[eventId]
            if (wrapTarget != null) {
                val (noteId, recipient) = wrapTarget
                val delivery = deliveries.value[noteId] ?: return

                deliveries.value =
                    deliveries.value +
                    (
                        noteId to
                            delivery.copy(
                                acceptedRelays = delivery.acceptedRelays + relay,
                                recipients =
                                    delivery.recipients?.map {
                                        if (it.recipient == recipient) {
                                            it.copy(acceptedRelays = it.acceptedRelays + relay)
                                        } else {
                                            it
                                        }
                                    },
                            )
                    )
            } else {
                val delivery = deliveries.value[eventId] ?: return
                deliveries.value =
                    deliveries.value + (eventId to delivery.copy(acceptedRelays = delivery.acceptedRelays + relay))
            }
        }
    }

    private fun registerNoteId(noteId: HexKey) {
        trackedOrder.addLast(noteId)
        if (trackedOrder.size > MAX_TRACKED) {
            val evicted = trackedOrder.removeFirst()
            deliveries.value = deliveries.value - evicted
            wrapIndex = wrapIndex.filterValues { it.first != evicted }
        }
    }

    companion object {
        // Delivery state is only rendered on recent messages; a bounded window keeps
        // the maps from growing for the whole session.
        private const val MAX_TRACKED = 500
    }
}
