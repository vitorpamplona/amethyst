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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

/**
 * Runs a NIP-46 remote signer ("bunker") for one account: subscribes to the
 * given [relays] for kind-24133 requests addressed to the user, decrypts each
 * one with the user's [signer], hands it to [processor], and publishes the
 * encrypted reply back to the requesting client.
 *
 * The signer is whatever the account logged in with — a local keypair or a
 * NIP-55 external app — so the same [NostrConnectEvent.create] transport
 * encryption and the same [BunkerRequestProcessor] dispatch serve both.
 *
 * [run] is a long-running suspend loop: it services requests until the calling
 * coroutine is cancelled, then tears the subscription down. Callers who need to
 * follow a changing relay set (e.g. the user editing their inbox relays) should
 * cancel and relaunch [run] with the new set.
 */
class NostrConnectSignerService(
    val client: INostrClient,
    val signer: NostrSigner,
    val processor: BunkerRequestProcessor,
    val relays: Set<NormalizedRelayUrl>,
    /** Optional hook, invoked with each serviced request's method + client, for logging/metrics. */
    val onServiced: ((method: String, clientPubKey: String, error: String?) -> Unit)? = null,
    /**
     * Upper bound on the request-id dedup set. A long-lived signer would otherwise
     * accumulate every request id it ever saw; past this many, the oldest ids are
     * evicted (they are far past any realistic same-request retry window).
     */
    val seenCap: Int = 4096,
) {
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

        val self = signer.pubKey
        val events = Channel<NostrConnectEvent>(UNLIMITED)
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
        val seen = LinkedHashSet<String>()
        val filter = Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(self)))
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
                handle(event)
            }
        } finally {
            client.unsubscribe(subId)
            events.close()
        }
    }

    private suspend fun handle(event: NostrConnectEvent) {
        val client = event.talkingWith(signer.pubKey)
        val request =
            try {
                event.decryptMessage(signer) as? BunkerRequest ?: return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NIP46Signer") { "could not decrypt request ${event.id.take(8)}: ${e.message}" }
                return
            }

        val response = processor.process(client, request)
        val error = (response as? BunkerResponseError)?.error
        onServiced?.invoke(request.method, client, error)

        try {
            val reply = NostrConnectEvent.create(response, client, signer)
            this.client.publish(reply, relays)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("NIP46Signer") { "failed to send reply for ${request.method}: ${e.message}" }
        }
    }
}
