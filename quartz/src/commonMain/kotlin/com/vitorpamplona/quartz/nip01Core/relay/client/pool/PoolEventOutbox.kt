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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

class PoolEventOutbox {
    /**
     * Pending publishes, keyed by event id.
     *
     * A concurrent map, NOT a copy-on-write immutable one. It used to be
     * `@Volatile var eventOutbox = mapOf(...)` reassigned with
     * `eventOutbox + Pair(...)`, which copies EVERY entry on EVERY publish —
     * so publishing N events cost O(N^2). Measured on a bulk push with ~970k
     * entries resident: 22.7ms per event, of which ~20.5ms was this map, and
     * the rate decayed as the outbox grew (45.6 -> 44.6 -> 43.2 ev/s across
     * three windows). The store fetch behind the same loop cost 1.2ms.
     *
     * [LargeCache] is ConcurrentHashMap on JVM/Android, so put/get/remove are
     * O(1) and cross-thread visibility no longer needs the volatile republish.
     */
    private val eventOutbox = LargeCache<HexKey, PoolEventOutboxState>()
    val relays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    /**
     * Removals since the relay set was last rebuilt.
     *
     * Deciding whether a relay may leave [relays] means asking whether ANY
     * remaining entry still wants it — O(outbox), and doing that per publish
     * is the second half of the quadratic. Additions stay exact and cheap (a
     * union of the event's own relays); removals are swept in batches, because
     * keeping a relay in the set slightly too long only means holding a
     * connection a little longer, while scanning a million entries to retire
     * it promptly costs the whole push.
     */
    @Volatile
    private var pendingSweep = 0

    companion object {
        /** Removals between full relay-set rebuilds — see [pendingSweep]. */
        private const val SWEEP_EVERY = 256
    }

    fun needsToUpdateRelays(): Boolean {
        val currentRelays = relays.value

        var relaysToRemoveCounter = 0

        currentRelays.forEach { currentRelay ->
            if (eventOutbox.values().none { currentRelay in it.relaysRemaining }) {
                relaysToRemoveCounter++
            }
        }

        var relaysToAddCounter = 0
        eventOutbox.values().forEach { outboxState ->
            if (outboxState.relaysRemaining.any { it !in currentRelays }) {
                relaysToAddCounter++
            }
        }

        return relaysToRemoveCounter > 0 || relaysToAddCounter > 0
    }

    fun updateRelays() {
        if (needsToUpdateRelays()) {
            relays.update { currentRelays ->
                val relaysToRemove = mutableSetOf<NormalizedRelayUrl>()

                currentRelays.forEach { currentRelay ->
                    if (eventOutbox.values().none { currentRelay in it.relaysRemaining }) {
                        relaysToRemove.add(currentRelay)
                    }
                }

                val relaysToAdd = mutableSetOf<NormalizedRelayUrl>()
                eventOutbox.values().forEach { outboxState ->
                    outboxState.relaysRemaining.forEach { relay ->
                        if (relay !in relaysToAdd && relay !in currentRelays) {
                            relaysToAdd.add(relay)
                        }
                    }
                }

                (currentRelays - relaysToRemove) + relaysToAdd
            }
        }
    }

    fun activeOutboxCacheFor(url: NormalizedRelayUrl): Set<HexKey> {
        val myEvents = mutableSetOf<HexKey>()
        eventOutbox.forEach { eventId, outboxCache ->
            if (url in outboxCache.relaysRemaining) {
                myEvents.add(eventId)
            }
        }
        return myEvents
    }

    /**
     * The events still pending delivery to [url]. Unlike [activeOutboxCacheFor] (ids only), this
     * returns the full events so callers can inspect kind/tags — e.g. to explain *why* a relay is
     * being authenticated with (a pending gift wrap => sending a DM to its recipient).
     */
    fun activeOutboxEventsFor(url: NormalizedRelayUrl): List<Event> {
        val myEvents = mutableListOf<Event>()
        eventOutbox.forEach { _, outboxCache ->
            if (url in outboxCache.relaysRemaining) {
                myEvents.add(outboxCache.event)
            }
        }
        return myEvents
    }

    /**
     * Returns the relays that have NOT yet acknowledged [eventId] with an OK, or
     * null if the event is not currently tracked (never sent or already fully done).
     * Callers can poll this after publish to detect when relays ack: the set shrinks
     * as OKs arrive, then the entry is removed from the outbox (returns null).
     */
    fun pendingRelaysFor(eventId: HexKey): Set<NormalizedRelayUrl>? = eventOutbox.get(eventId)?.relaysLeft()

    fun markAsSending(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> {
        val currentOutbox = eventOutbox.get(event.id)
        if (currentOutbox == null) {
            eventOutbox.put(event.id, PoolEventOutboxState(event, relays))
        } else {
            currentOutbox.updateRelays(relays)
        }
        // Additions only, and only what is genuinely new: the union is over
        // this event's relays, never over the whole outbox.
        addRelays(relays)
        return eventOutbox.get(event.id)?.remainingRelays() ?: emptySet()
    }

    /** Union [wanted] into [relays], touching the flow only when it actually changes. */
    private fun addRelays(wanted: Set<NormalizedRelayUrl>) {
        val missing = wanted - relays.value
        if (missing.isNotEmpty()) relays.update { it + missing }
    }

    /**
     * An entry left the outbox. Retiring its relays needs a full scan, so that
     * is amortised across [SWEEP_EVERY] removals — and always run once the
     * outbox empties, which is the case that must not linger.
     */
    private fun onRemoved() {
        pendingSweep++
        if (pendingSweep >= SWEEP_EVERY || eventOutbox.isEmpty()) {
            pendingSweep = 0
            updateRelays()
        }
    }

    /** Records a send attempt. Returns the event if this attempt exhausted its retry budget for
     *  [url] (i.e. we gave up delivering it there), or null otherwise. */
    fun newTry(
        id: HexKey,
        url: NormalizedRelayUrl,
    ): Event? {
        val waiting = eventOutbox.get(id) ?: return null
        val gaveUp = waiting.newTry(url)
        if (waiting.isDone()) {
            eventOutbox.remove(waiting.event.id)
            onRemoved()
        }
        return if (gaveUp) waiting.event else null
    }

    fun newResponse(
        id: HexKey,
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
    ) {
        val waiting = eventOutbox.get(id)
        if (waiting != null) {
            waiting.newResponse(url, success, message)
            if (waiting.isDone()) {
                eventOutbox.remove(waiting.event.id)
                onRemoved()
            }
        }
    }

    // --------------------------
    // State management functions
    // --------------------------
    suspend fun syncState(
        relay: NormalizedRelayUrl,
        sync: (Command) -> Unit,
    ) {
        eventOutbox.forEach { _, outboxCache ->
            outboxCache.forEachUnsentEvent(relay) {
                sync(EventCmd(it))
            }
        }
    }

    suspend fun onIncomingMessage(
        relay: NormalizedRelayUrl,
        msg: Message,
    ) {
        if (msg is OkMessage) {
            newResponse(msg.eventId, relay, msg.success, msg.message)
        }
    }

    /** Returns the event if this send attempt made us give up delivering it to [relay]. */
    fun onSent(
        relay: NormalizedRelayUrl,
        cmd: Command,
    ): Event? = if (cmd is EventCmd) newTry(cmd.event.id, relay) else null

    fun sendToRelayIfChanged(
        event: Event,
        relaysToUpdate: Set<NormalizedRelayUrl>,
        sync: (NormalizedRelayUrl, Command) -> Unit,
    ) {
        relaysToUpdate.forEach { relay ->
            sync(relay, EventCmd(event))
        }
    }

    /**
     * If cannot connect, closes subs
     */
    fun onCannotConnect(
        relay: NormalizedRelayUrl,
        errorMessage: String,
    ) {
        eventOutbox.forEach { id, outboxCache ->
            if (relay in outboxCache.relaysRemaining) {
                newResponse(id, relay, false, errorMessage)
            }
        }
    }

    fun destroy() {
        eventOutbox.clear()
        pendingSweep = 0
        relays.tryEmit(emptySet())
    }
}
