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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Opens gift wraps, the seals inside them, and the account's drafts, several at a time as soon as they reach the cache,
 * ahead of [EventProcessor], which indexes them one note at a time.
 *
 * The indexing stays serial: its handlers share chat rooms, Concord sessions and the MLS Welcome
 * flow. Only the signer round trips move ahead of it. With a remote signer (NIP-46) each one is a
 * relay round trip of ~0.7 s, and the serial pass used to wait for them one by one: a cold start
 * that opened ~110 messages spent over a minute with exactly one request in flight. Relays stream
 * DMs in a few per batch, so the wraps are taken as each batch arrives ([offer]), not when the
 * serial pass reaches it. A local key or a NIP-55 app answers in milliseconds, so for them this
 * changes little.
 *
 * The serial pass decrypts through [signer], which hands each prefetched plaintext back once and
 * passes everything else, misses included, to the account's signer.
 */
internal class GiftWrapPrefetch(
    private val scope: CoroutineScope,
    private val accountSigner: () -> NostrSigner,
    /** Fills the account's draft cache for one of its drafts (NIP-37), the same serial decrypt. */
    private val warmDraft: suspend (DraftWrapEvent) -> Unit = {},
) {
    private val gate = Semaphore(MAX_IN_FLIGHT)

    // Ciphertext -> its plaintext, in flight or done, until the serial pass claims it.
    private val pending = LargeCache<String, CompletableDeferred<String>>()

    // Draft id -> its warm-up, while it runs: the handler waits for it rather than asking again.
    private val drafts = LargeCache<HexKey, Job>()

    /** What the serial pass decrypts gift wraps and seals with. */
    val signer: NostrSigner = PrefetchingSigner()

    /** Starts opening the wraps in a new batch of events that are addressed to this account. */
    fun offer(events: Collection<Event>) {
        val me = accountSigner().pubKey
        events.forEach { event ->
            if (event is GiftWrapEvent &&
                event.innerEventId == null &&
                event.content.isNotEmpty() &&
                event.recipientPubKey() == me &&
                !pending.containsKey(event.content)
            ) {
                prefetch(event)
            } else if (event is DraftWrapEvent &&
                event.pubKey == me &&
                !event.isDeleted() &&
                !drafts.containsKey(event.id)
            ) {
                val job = scope.launch(start = CoroutineStart.LAZY) { gate.withPermit { warmDraft(event) } }
                drafts.put(event.id, job)
                job.invokeOnCompletion { drafts.remove(event.id) }
                job.start()
            }
        }
    }

    /** Waits for [draft]'s warm-up if one is running, so the handler finds it in the draft cache. */
    suspend fun awaitDraft(draft: DraftWrapEvent) {
        drafts.get(draft.id)?.join()
    }

    private fun prefetch(wrap: GiftWrapEvent) {
        val wrapPlaintext = expect(wrap.content)
        scope.launch {
            try {
                val plaintext = gate.withPermit { accountSigner().nip44Decrypt(wrap.content, wrap.pubKey) }
                // The seal's slot first, so the serial pass, woken by the wrap's answer, never
                // reaches the seal before it is registered and asks a second time.
                sealIn(plaintext)?.let { seal ->
                    val sealPlaintext = expect(seal.content)
                    scope.launch { decryptInto(sealPlaintext, seal.content, seal.pubKey) }
                }
                wrapPlaintext.complete(plaintext)
            } catch (e: Exception) {
                // The serial pass gets the same failure it would have met itself.
                wrapPlaintext.completeExceptionally(e)
                if (e is CancellationException) throw e
            }
        }
    }

    private suspend fun decryptInto(
        result: CompletableDeferred<String>,
        ciphertext: String,
        from: HexKey,
    ) {
        try {
            result.complete(gate.withPermit { accountSigner().nip44Decrypt(ciphertext, from) })
        } catch (e: Exception) {
            result.completeExceptionally(e)
            if (e is CancellationException) throw e
        }
    }

    private fun expect(ciphertext: String): CompletableDeferred<String> {
        val result = CompletableDeferred<String>()
        pending.put(ciphertext, result)
        // An answer the serial pass never claims (a seal it already had) is dropped after a while.
        scope.launch {
            delay(UNCLAIMED_MS)
            if (pending.get(ciphertext) === result) pending.remove(ciphertext)
        }
        return result
    }

    private fun sealIn(plaintext: String): SealEvent? =
        try {
            (Event.fromJson(plaintext) as? SealEvent)?.takeIf { it.innerEventId == null && it.content.isNotEmpty() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    private inner class PrefetchingSigner : NostrSigner(accountSigner().pubKey) {
        private val inner: NostrSigner get() = accountSigner()

        override val pubKey: HexKey get() = inner.pubKey

        override fun isWriteable() = inner.isWriteable()

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = pending.remove(ciphertext)?.await() ?: inner.nip44Decrypt(ciphertext, fromPublicKey)

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    companion object {
        /**
         * Requests in flight at once. A NIP-46 bunker answered 23–33 parallel requests in 8–10 s
         * each at a cold start, against ~0.7 s for one at a time: past a handful, more only queue
         * at the signer and its relays.
         */
        const val MAX_IN_FLIGHT = 8

        const val UNCLAIMED_MS = 60_000L
    }
}
