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
package com.vitorpamplona.quartz.nip46RemoteSigner.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * Runs a NIP-46 remote signer ("bunker") for one account: subscribes to the
 * given [relays] for kind-24133 requests addressed to the bunker, decrypts each
 * envelope, hands the request to [processor], and publishes the encrypted reply.
 *
 * Two keys are deliberately kept apart:
 *  - [transportSigner] wraps and unwraps the kind-24133 envelope (subscription
 *    p-tag, [NostrConnectEvent.decryptMessage] / [NostrConnectEvent.create]). It
 *    is a dedicated per-account key that has nothing to do with the user's
 *    identity, so the bunker address and the on-relay traffic don't reveal WHO
 *    the bunker is for, and — since it is a local key — the envelope crypto never
 *    round-trips an external NIP-55 signer.
 *  - The [processor]'s signer is the user's IDENTITY signer (a local key or a
 *    NIP-55 app); it performs the actual `sign_event`/`nip04|44_*` work and
 *    answers `get_public_key` with the real npub — revealed only to a connected
 *    client, over the already-encrypted channel.
 *
 * [run] is a long-running suspend loop: it services requests until the calling
 * coroutine is cancelled, then tears the subscription down. Callers who need to
 * follow a changing relay set (e.g. the user editing their inbox relays) should
 * cancel and relaunch [run] with the new set.
 */
class NostrConnectSignerService(
    val client: INostrClient,
    val transportSigner: NostrSigner,
    val processor: BunkerRequestProcessor,
    val relays: Set<NormalizedRelayUrl>,
    /** Optional hook, invoked with each serviced request + client, for logging/metrics/activity feeds. */
    val onServiced: ((request: BunkerRequest, clientPubKey: String, error: String?) -> Unit)? = null,
    /**
     * Upper bound on the dedup set of seen kind-24133 **event ids** (the wrapper events, so the same
     * request fanned in from multiple relays is handled once). A long-lived signer would otherwise
     * accumulate every event id it saw; past this many the oldest are evicted (far past any realistic
     * same-event redelivery window). NOTE: this is keyed by the wrapper event id, not the inner NIP-46
     * request id, and it does not survive a service restart — the [maxRequestAgeSeconds] gate is what
     * suppresses relay replays of old requests across re-subscriptions.
     */
    val seenCap: Int = 4096,
    /**
     * Bound on events buffered between the relay threads and the single consumer.
     * Under a flood (a looping client, or a hostile peer p-tagging us) the newest
     * events past this many are dropped instead of growing memory without limit.
     * Envelope decryption is local, but each serviced request can drive an external
     * NIP-55 op on the identity signer, so the queue must not run away.
     */
    val maxQueue: Int = 256,
    /** Max requests decrypted per author within [rateWindowSeconds] before further ones are dropped. */
    val maxRequestsPerWindow: Int = 40,
    val rateWindowSeconds: Long = 10,
    /** Cap on distinct authors tracked for rate-limiting (evicts oldest) so key-rotation can't grow it. */
    val maxTrackedAuthors: Int = 512,
    /**
     * Requests older than this (by `created_at`) are ignored. kind-24133 is ephemeral, but many relays
     * store and REPLAY it whenever we re-subscribe (a relay-set change, reconnect, toggle, or rotation),
     * and the in-memory dedup set does not survive that restart — so without an age gate the same
     * minutes-old request gets signed again. Applied both as a `since` on the subscription (compliant
     * relays never replay it) and as a receive-side guard (for relays that ignore `since`). The window
     * must exceed realistic client/relay clock skew so a genuinely fresh request is never dropped, but
     * kept small so an app restart re-signs as little as possible (relays replay only this far back).
     */
    val maxRequestAgeSeconds: Long = 30,
    /**
     * Event ids serviced in a previous run, used to seed the in-memory dedup set so a relay replaying
     * stored requests across an app restart is caught by EXACT event id — immune to client clock skew,
     * unlike a timestamp floor (which would wrongly drop a second app whose clock lags). The host
     * persists these (bounded) and feeds them back on start; see [onHandledId].
     */
    val initialSeen: Set<String> = emptySet(),
    /** Invoked with each serviced request's kind-24133 event id so the host can persist it for [initialSeen]. */
    val onHandledId: (suspend (eventId: String) -> Unit)? = null,
) {
    /**
     * Fixed-window per-author rate limit. Touched only by the single consumer
     * coroutine (never the relay threads), so a plain map needs no synchronization.
     */
    private class RateLimiter(
        val maxPerWindow: Int,
        val windowSeconds: Long,
        val maxAuthors: Int,
    ) {
        private class Window(
            var start: Long,
            var count: Int,
        )

        private val windows = LinkedHashMap<String, Window>()

        fun allow(
            author: String,
            now: Long,
        ): Boolean {
            val window = windows.getOrPut(author) { Window(now, 0) }
            if (now - window.start >= windowSeconds) {
                window.start = now
                window.count = 0
            }
            if (windows.size > maxAuthors) {
                windows.iterator().let {
                    it.next()
                    it.remove()
                }
            }
            if (window.count >= maxPerWindow) return false
            window.count++
            return true
        }
    }

    /**
     * Subscribes and services requests until cancelled. Duplicate events (the
     * same request seen on more than one relay) are handled once. Never returns
     * normally — it loops until the coroutine is cancelled.
     */
    suspend fun run() {
        if (relays.isEmpty()) {
            Log.w("NIP46Signer") { "no relays to listen on; signer service is idle" }
            return
        }

        val self = transportSigner.pubKey
        // Bounded + DROP_LATEST so a flood bounds memory instead of growing an unlimited queue.
        val events = Channel<NostrConnectEvent>(capacity = maxQueue, onBufferOverflow = BufferOverflow.DROP_LATEST)
        val rateLimiter = RateLimiter(maxRequestsPerWindow, rateWindowSeconds, maxTrackedAuthors)
        val subId = newSubId()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // onEvent is invoked CONCURRENTLY from each relay's socket thread, so it must
                    // touch no shared mutable state here — the (thread-safe) channel send is all it
                    // does; dedup happens in the single-threaded consumer below.
                    if (event is NostrConnectEvent && event.verifiedRecipientPubKey() == self) {
                        events.trySend(event)
                    }
                }
            }

        // Insertion-ordered dedup, confined to this one consumer coroutine (never the relay threads);
        // evicts the oldest id past the cap so a long-lived signer can't grow it without bound.
        // Seed the dedup set with ids serviced in a prior run so a relay replaying stored requests after
        // a restart is caught by exact id (see [initialSeen]).
        val seen = LinkedHashSet(initialSeen)
        // Only ask relays for recent requests: kind-24133 is ephemeral, but relays that store it would
        // otherwise replay every old request each time we (re)subscribe. See [maxRequestAgeSeconds].
        val filter = Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(self)), since = TimeUtils.now() - maxRequestAgeSeconds)
        client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
        try {
            while (true) {
                val event = events.receive()
                if (!seen.add(event.id)) continue // same request already handled (arrived on another relay)
                if (seen.size > seenCap) {
                    seen.iterator().let {
                        it.next()
                        it.remove()
                    }
                }
                // Drop stale requests a relay replayed from storage past the rolling age window (the
                // `since` filter covers compliant relays; this covers the rest; exact-id replays within
                // the window are already caught by [seen] above). A live NIP-46 request is seconds old.
                if (TimeUtils.now() - event.createdAt > maxRequestAgeSeconds) {
                    Log.w("NIP46Signer") { "ignoring stale request ${event.id.take(8)}… (created ${event.createdAt})" }
                    continue
                }
                // Rate-limit per author BEFORE decrypting — decryption can be an external-signer
                // round-trip, so a flooding client must not force one per event.
                if (!rateLimiter.allow(event.pubKey, TimeUtils.now())) {
                    Log.w("NIP46Signer") { "rate-limited request from ${event.pubKey.take(8)}…" }
                    continue
                }
                handle(event)
                // Remember this id (persisted by the host) so a later restart won't re-service the replay.
                onHandledId?.invoke(event.id)
            }
        } finally {
            client.unsubscribe(subId)
            events.close()
        }
    }

    private suspend fun handle(event: NostrConnectEvent) {
        val client = event.talkingWith(transportSigner.pubKey)
        val request =
            try {
                event.decryptMessage(transportSigner) as? BunkerRequest ?: return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NIP46Signer") { "could not decrypt request ${event.id.take(8)}: ${e.message}" }
                return
            }

        val response = processor.process(client, request)
        val error = (response as? BunkerResponseError)?.error
        onServiced?.invoke(request, client, error)

        try {
            val reply = NostrConnectEvent.create(response, client, transportSigner)
            this.client.publish(reply, relays)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NIP46Signer") { "failed to send reply for ${request.method}: ${e.message}" }
        }
    }
}
