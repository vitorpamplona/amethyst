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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One relay's refusal of a published event — its `OK false` reason, verbatim, plus the NIP-01
 * machine-readable [prefix] when the relay supplied one.
 *
 * This is the only thing a relay ever says about *why* a message didn't land. A Buzz tenant ban
 * (kind 9040) or timeout (9042) reaches the client here and nowhere else: the relay enforces it at
 * publish time and answers `restricted: ...`, with no event for the client to fold.
 */
@Immutable
data class RelayRejection(
    val relay: NormalizedRelayUrl,
    val reason: String,
    val prefix: MachineReadablePrefix? = MachineReadablePrefix.parse(reason),
) {
    /** True when retrying the same event unchanged could plausibly succeed. */
    val isTransient: Boolean
        get() =
            when (prefix) {
                MachineReadablePrefix.RATE_LIMITED, MachineReadablePrefix.ERROR -> true
                // A refusal we can act on: authenticate, then republish.
                MachineReadablePrefix.AUTH_REQUIRED -> true
                // blocked/restricted (bans, timeouts, non-members), invalid, unsupported, pow:
                // resending the identical event changes nothing. Unprefixed reasons are unknown,
                // and claiming a retry will help would be a guess.
                else -> false
            }
}

/** Delivery progress of one recipient's gift wrap (NIP-17 DMs). */
@Immutable
data class RecipientDelivery(
    val recipient: HexKey,
    val targetRelays: Set<NormalizedRelayUrl>,
    val acceptedRelays: Set<NormalizedRelayUrl> = emptySet(),
    val rejections: List<RelayRejection> = emptyList(),
    // The sender's own self-copy wrap: shown in the delivery detail (it matters
    // for multi-device sync) but excluded from "delivered to everyone" and the
    // k/n count, which describe the OTHER participants.
    val isSelf: Boolean = false,
) {
    val isDelivered: Boolean
        get() = acceptedRelays.isNotEmpty()

    /** Every target relay refused and none accepted — this recipient's wrap did not land. */
    val isRefused: Boolean
        get() = acceptedRelays.isEmpty() && rejections.isNotEmpty() && rejections.mapTo(HashSet()) { it.relay }.containsAll(targetRelays)
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
    val rejections: List<RelayRejection> = emptyList(),
    val recipients: List<RecipientDelivery>? = null,
) {
    /** The other participants' wraps (self-copy excluded); null for rooms. */
    val otherRecipients: List<RecipientDelivery>?
        get() = recipients?.filterNot { it.isSelf }

    val isFullyAccepted: Boolean
        get() {
            val others = otherRecipients
            return if (others != null) {
                others.isNotEmpty() && others.all { it.isDelivered }
            } else {
                targetRelays.isNotEmpty() && acceptedRelays.containsAll(targetRelays)
            }
        }

    /**
     * The message reached nobody: every target relay answered `OK false` and none accepted.
     *
     * Deliberately strict. A relay that simply hasn't answered yet leaves this false — the message
     * is still in flight, not refused — and one relay refusing while another accepts is a delivered
     * message, so the refusal belongs in the detail view rather than on the bubble. The strictness
     * is what makes the true case worth showing: on a single-relay room (a Buzz workspace channel)
     * it fires on the first refusal.
     */
    val isRefused: Boolean
        get() {
            val others = otherRecipients
            return if (others != null) {
                others.isNotEmpty() && others.all { it.isRefused }
            } else {
                acceptedRelays.isEmpty() && rejections.isNotEmpty() && rejections.mapTo(HashSet()) { it.relay }.containsAll(targetRelays)
            }
        }

    /** A representative refusal to show on the bubble; the detail view lists them all. */
    val firstRejection: RelayRejection?
        get() = rejections.firstOrNull() ?: otherRecipients?.firstNotNullOfOrNull { it.rejections.firstOrNull() }

    /** Rejections keyed by relay, for the per-relay detail rows. */
    fun rejectionFor(relay: NormalizedRelayUrl): RelayRejection? = rejections.firstOrNull { it.relay == relay }
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
 * State is held as one small StateFlow per tracked message, so a relay OK updates
 * and notifies only that message's collectors instead of fanning out through a
 * whole-map flow. In-memory only: history from before an app restart simply has no
 * entry, and the UI falls back to the Note's seen-on relays.
 *
 * Owns a persistent listener on the client: [destroy] MUST be called when the
 * owning Account is discarded (logout / account removal) or the listener leaks.
 */
class ChatDeliveryTracker(
    client: INostrClient,
) {
    private val lock = Any()

    // One flow per tracked (or queried) displayed-note id; LinkedHashMap iteration
    // order is insertion order, used for eviction. Guarded by [lock].
    private val deliveries = LinkedHashMap<HexKey, MutableStateFlow<ChatDelivery?>>()

    // Lock-free fast-path index for the OK listener, which fires for every event
    // the app publishes anywhere: wrap id -> (displayed note id, recipient).
    @Volatile
    private var wrapIndex = mapOf<HexKey, Pair<HexKey, HexKey>>()

    // Lock-free fast-path set of tracked/queried note ids (see [onAccepted]).
    @Volatile
    private var knownIds = setOf<HexKey>()

    private val okCollector =
        RelayInsertConfirmationCollector(
            client,
            onRelayRejected = { eventId, relay, reason -> onRejected(eventId, relay.url, reason) },
        ) { eventId, relay ->
            onAccepted(eventId, relay.url)
        }

    /** Registers a public room message published to the room's [targetRelays]. */
    fun trackPublic(
        eventId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        if (targetRelays.isEmpty()) return
        synchronized(lock) {
            flowForLocked(eventId).value = ChatDelivery(targetRelays)
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
        isSelf: Boolean = false,
    ) {
        synchronized(lock) {
            val flow = flowForLocked(displayedNoteId)
            val current = flow.value

            flow.value =
                ChatDelivery(
                    targetRelays = (current?.targetRelays ?: emptySet()) + targetRelays,
                    acceptedRelays = current?.acceptedRelays ?: emptySet(),
                    rejections = current?.rejections ?: emptyList(),
                    recipients = (current?.recipients ?: emptyList()) + RecipientDelivery(recipient, targetRelays, isSelf = isSelf),
                )

            wrapIndex = wrapIndex + (wrapId to (displayedNoteId to recipient))
        }
    }

    /**
     * Registers a group message that was broadcast as a single encrypted [wrapId] to
     * the group's [targetRelays] (Concord channels: the whole channel shares one
     * plane wrap, not a per-recipient gift wrap). Relays OK the wrap, but the feed
     * shows the decrypted inner rumor, so [displayedNoteId] (the rumor id) is mapped
     * through [wrapId] — like [trackPublic] (relay ticks, no per-recipient breakdown)
     * but keyed by the wrap the relay actually acknowledges.
     */
    fun trackWrappedPublic(
        displayedNoteId: HexKey,
        wrapId: HexKey,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        if (targetRelays.isEmpty()) return
        synchronized(lock) {
            flowForLocked(displayedNoteId).value = ChatDelivery(targetRelays)
            // recipient is unused for a relay-only delivery (recipients stays null, so
            // onAccepted never matches on it); reuse the note id as a harmless value.
            wrapIndex = wrapIndex + (wrapId to (displayedNoteId to displayedNoteId))
        }
    }

    fun deliveryFlow(noteId: HexKey): StateFlow<ChatDelivery?> =
        synchronized(lock) {
            flowForLocked(noteId)
        }

    fun currentFor(noteId: HexKey): ChatDelivery? =
        synchronized(lock) {
            deliveries[noteId]?.value
        }

    fun destroy() {
        okCollector.destroy()
        synchronized(lock) {
            deliveries.clear()
            wrapIndex = emptyMap()
            knownIds = emptySet()
        }
    }

    private fun onAccepted(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        // Lock-free negative path: OKs fire for every event the app publishes
        // anywhere; almost all are not chat messages we track.
        if (eventId !in wrapIndex && eventId !in knownIds) return

        synchronized(lock) {
            val wrapTarget = wrapIndex[eventId]
            if (wrapTarget != null) {
                val (noteId, recipient) = wrapTarget
                val flow = deliveries[noteId] ?: return
                val delivery = flow.value ?: return

                flow.value =
                    delivery.copy(
                        acceptedRelays = delivery.acceptedRelays + relay,
                        // A relay that accepted is no longer refusing: the auth-required -> AUTH ->
                        // republish round trip refuses first and then stores, and leaving the stale
                        // refusal behind would report a delivered message as rejected.
                        rejections = delivery.rejections.filterNot { it.relay == relay },
                        recipients =
                            delivery.recipients?.map {
                                if (it.recipient == recipient) {
                                    it.copy(
                                        acceptedRelays = it.acceptedRelays + relay,
                                        rejections = it.rejections.filterNot { r -> r.relay == relay },
                                    )
                                } else {
                                    it
                                }
                            },
                    )
            } else {
                val flow = deliveries[eventId] ?: return
                val delivery = flow.value ?: return
                flow.value =
                    delivery.copy(
                        acceptedRelays = delivery.acceptedRelays + relay,
                        rejections = delivery.rejections.filterNot { it.relay == relay },
                    )
            }
        }
    }

    /**
     * A relay refused [eventId]. Recorded per relay so the bubble can say the message didn't land and
     * the detail view can quote the reason — the only place a relay-side ban, timeout, rate limit or
     * unsupported kind ever becomes visible to the sender.
     */
    private fun onRejected(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        // `duplicate:` means the relay already holds the event, which is delivery, not refusal.
        // Relays disagree on whether to pair it with OK true or OK false, so normalize here.
        if (MachineReadablePrefix.parse(reason) == MachineReadablePrefix.DUPLICATE) {
            onAccepted(eventId, relay)
            return
        }

        // Lock-free negative path, as in [onAccepted].
        if (eventId !in wrapIndex && eventId !in knownIds) return

        synchronized(lock) {
            val rejection = RelayRejection(relay, reason)
            val wrapTarget = wrapIndex[eventId]
            if (wrapTarget != null) {
                val (noteId, recipient) = wrapTarget
                val flow = deliveries[noteId] ?: return
                val delivery = flow.value ?: return

                flow.value =
                    delivery.copy(
                        rejections = delivery.rejections.addingOnce(rejection),
                        recipients =
                            delivery.recipients?.map {
                                if (it.recipient == recipient) {
                                    it.copy(rejections = it.rejections.addingOnce(rejection))
                                } else {
                                    it
                                }
                            },
                    )
            } else {
                val flow = deliveries[eventId] ?: return
                val delivery = flow.value ?: return
                flow.value = delivery.copy(rejections = delivery.rejections.addingOnce(rejection))
            }
        }
    }

    /**
     * Appends [rejection] unless this relay already has one recorded — a relay can repeat an `OK
     * false` across reconnects/retries, and the detail view shows one row per relay.
     */
    private fun List<RelayRejection>.addingOnce(rejection: RelayRejection): List<RelayRejection> = if (any { it.relay == rejection.relay }) this else this + rejection

    // Must run under [lock]. Also creates entries for ids queried by the UI
    // before their send registers (compose can win that race), so both paths
    // share the same flow instance.
    private fun flowForLocked(noteId: HexKey): MutableStateFlow<ChatDelivery?> {
        deliveries[noteId]?.let { return it }

        val flow = MutableStateFlow<ChatDelivery?>(null)
        deliveries[noteId] = flow
        knownIds = knownIds + noteId

        while (deliveries.size > MAX_TRACKED) {
            val evicted = deliveries.keys.first()
            deliveries.remove(evicted)
            knownIds = knownIds - evicted
            wrapIndex = wrapIndex.filterValues { it.first != evicted }
        }

        return flow
    }

    companion object {
        // Delivery state is only rendered on recent messages; a bounded window keeps
        // the maps from growing for the whole session.
        private const val MAX_TRACKED = 500
    }
}
