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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock
import kotlin.concurrent.Volatile

/**
 * Delivery bookkeeping for one published event: which relays still owe an OK, and the sends per
 * relay that no OK has answered yet.
 *
 * Each relay's OK arrives on that relay's socket thread, so the two collections are guarded by one
 * lock; unguarded, two OKs landing together lost one and the event never finished. They are mutable
 * on purpose: copying an immutable set on every ack made a publish O(relays^2), and measured (JMH,
 * a send and an OK per relay) the lock costs nothing next to those copies: about 145 ns per relay
 * whatever the relay count, against 360 ns at 5 relays and 3,400 ns at 100 with copy-on-write.
 * Readers that want the set get a copy; [isDone], read after every OK, is a volatile count.
 */
class PoolEventOutboxState(
    val event: Event,
    relays: Set<NormalizedRelayUrl>,
    /** [event]'s JSON as the publisher already held it, sent verbatim on every try; see [EventCmd.eventJson]. */
    @Volatile var eventJson: String? = null,
) {
    private val lock = PlatformLock()

    // Guarded by [lock].
    private val remaining = HashSet(relays)

    // Guarded by [lock]; created on the first send.
    private var failures: HashMap<NormalizedRelayUrl, Tries>? = null

    @Volatile private var remainingCount = remaining.size

    // Guarded by [lock]; created on the first `rate-limited:` refusal with a back-off. See [newResponse].
    private var backOffs: HashMap<NormalizedRelayUrl, Int>? = null

    // Guarded by [lock]; created on the first `auth-required:` that keeps the event. See [newResponse].
    private var authWaits: HashMap<NormalizedRelayUrl, Int>? = null

    fun updateRelays(newRelays: Set<NormalizedRelayUrl>) {
        lock.withLock {
            remaining.clear()
            remaining.addAll(newRelays)
            remainingCount = remaining.size
        }
    }

    fun isDone() = remainingCount == 0

    /** A copy of the relays still owed this event. */
    fun relaysLeft(): Set<NormalizedRelayUrl> = lock.withLock { remaining.toSet() }

    fun isSupposedToGo(url: NormalizedRelayUrl) = lock.withLock { url in remaining }

    fun forEachUnsentEvent(
        url: NormalizedRelayUrl,
        run: (url: Event) -> Unit,
    ) = if (isSupposedToGo(url)) run(event) else null

    /** The EVENT command a send of this event puts on the wire. */
    fun command() = EventCmd(event, eventJson)

    fun remainingRelays() = relaysLeft()

    // Under [lock].
    private fun drop(url: NormalizedRelayUrl) {
        if (remaining.remove(url)) remainingCount = remaining.size
    }

    // Under [lock].
    private fun failuresMap() = failures ?: HashMap<NormalizedRelayUrl, Tries>(4).also { failures = it }

    /** Records a send attempt to [url]. Returns true if the retry budget is now exhausted and the
     *  relay was dropped (i.e. we gave up delivering this event to [url]). */
    fun newTry(url: NormalizedRelayUrl): Boolean =
        lock.withLock {
            val f = failuresMap()
            val currentTries = f[url]
            if (currentTries != null) {
                currentTries.addTriedTime(TimeUtils.now())
                if (currentTries.isDone()) {
                    drop(url)
                    f.remove(url)
                    return@withLock true
                }
            } else {
                f[url] = Tries(listOf(TimeUtils.now()))
            }
            false
        }

    /**
     * The relay's `OK` for this event. Any `OK`, true or false, is the relay's verdict and ends
     * delivery to [url]: the outbox exists to re-send what a dropped connection lost, not to argue
     * with an answer. Re-sending a refusal only drew the same refusal on every reconnect, and until
     * three had piled up the event stayed resident: a bulk publisher whose events a relay refused
     * (a tag or size limit) kept every one of them in memory for the life of the socket.
     *
     * Two answers say "later", not "no", and keep the event pending: `auth-required:`, re-sent once
     * the AUTH lands, and a `rate-limited:` that states its back-off, re-sent after it (bounded by
     * [MAX_BACK_OFFS]).
     *
     * An `auth-required:` is "later" only while an AUTH can still land: [authMayLand] says whether
     * anyone will answer this relay's challenge (a responder is attached, and this relay's last
     * AUTH did not fail). Without that it is the verdict. Even with it, at most [MAX_AUTH_WAITS]
     * such answers keep the event: each one used to reset the retry budget, so a relay demanding an
     * AUTH nobody signs kept the event in memory, and re-sent it on every reconnect, forever.
     */
    fun newResponse(
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
        retryAfterMs: Long? = null,
        authMayLand: () -> Boolean = { true },
    ) {
        lock.withLock {
            if (!success && retryAfterMs != null && (backOffs?.get(url) ?: 0) < MAX_BACK_OFFS) {
                // NIP-01 `rate-limited:` with a back-off: the relay is up and will take the event
                // later; the pool re-sends it once the back-off passes. Not a failure of the event,
                // so it must not spend the retry budget — a busy relay pacing a burst would otherwise
                // drop events it was about to accept. Bounded, so a relay that only ever says
                // "later" is in the end taken at its word.
                val b = backOffs ?: HashMap<NormalizedRelayUrl, Int>(2).also { backOffs = it }
                b[url] = (b[url] ?: 0) + 1
                failures?.remove(url)
            } else if (!success && message.isAuthRequired() && (authWaits?.get(url) ?: 0) < MAX_AUTH_WAITS && authMayLand()) {
                // NIP-42 AUTH challenge in flight. The relay responded, so it's up and simply wants
                // auth first: RelayAuthenticator signs, the relay re-issues OK, and syncFilters()
                // re-pumps this outbox to retry the publish. Reset the retry budget for this relay
                // so the send attempts accumulated across reconnects / slow AUTH rounds can't exhaust
                // the cap and drop the event before AUTH lands: newTry() (the send path) grows
                // `tries` and is NOT auth-aware. The relay stays in [remaining]: the event must stay
                // pending for this relay until AUTH unlocks it.
                val w = authWaits ?: HashMap<NormalizedRelayUrl, Int>(2).also { authWaits = it }
                w[url] = (w[url] ?: 0) + 1
                failures?.remove(url)
            } else {
                drop(url)
                failures?.remove(url)
                backOffs?.remove(url)
                authWaits?.remove(url)
            }
        }
    }

    companion object {
        /** `rate-limited:` back-offs one relay may state for one event before they count as failures. */
        const val MAX_BACK_OFFS = 10

        /** `auth-required:` answers that keep one event pending for one relay before it is refused for good. */
        const val MAX_AUTH_WAITS = 10
    }

    /**
     * NIP-42 machine-readable prefix a relay uses to tell us an event was held
     * back pending authentication. The event should be re-sent after AUTH, not
     * retried-then-discarded like an ordinary failure.
     */
    fun String.isAuthRequired() = this.startsWith("auth-required:") || this == "auth-required"

    /** Send attempts to one relay that no `OK` has answered; the fourth gives up on the relay. */
    class Tries(
        var tries: List<Long> = listOf(),
    ) {
        fun isDone() = tries.size > 3

        fun addTriedTime(tried: Long) {
            tries += tried
        }
    }
}
