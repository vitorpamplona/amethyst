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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrency guarantees the service relies on when it fans requests out into child coroutines so
 * their consent prompts can batch: the identity signer's crypto must never run concurrently (an
 * external NIP-55 app can't take overlapping IPC ops), while authorization — which may block on a
 * user prompt for a long time — must NOT hold that lock, so a pending prompt can't stall other
 * clients' signing.
 */
class BunkerRequestProcessorConcurrencyTest {
    private val userPubKey = "a".repeat(64)
    private val clientPubKey = "c".repeat(64)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun signTemplate(id: String) = BunkerRequestSign(id, EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hi"))

    /** A signer whose sign() parks on [signGate] so tests can observe how many run at once. */
    private class GatedSigner(
        pubKey: HexKey,
        val signGate: CompletableDeferred<Unit>,
    ) : NostrSigner(pubKey) {
        var inFlight = 0
        var maxConcurrent = 0
        var signCount = 0

        val canned = Event(id = "e".repeat(64), pubKey = pubKey, createdAt = 1L, kind = 1, tags = emptyArray(), content = "s", sig = "f".repeat(128))

        override fun isWriteable() = true

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T {
            inFlight++
            maxConcurrent = maxOf(maxConcurrent, inFlight)
            signGate.await()
            inFlight--
            signCount++
            return canned as T
        }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = ""

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ""

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = ""

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ""

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = throw NotImplementedError()

        override suspend fun deriveKey(nonce: HexKey): HexKey = throw NotImplementedError()

        override suspend fun signPsbt(psbtHex: String): String = throw NotImplementedError()

        override fun hasForegroundSupport() = false
    }

    /** authorize() parks on the deferred returned by [gateFor] (null = allow immediately). */
    private class GatedAuthorizer(
        val gateFor: (BunkerRequest) -> CompletableDeferred<Boolean>?,
    ) : Nip46RequestAuthorizer {
        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept("ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ): Boolean = gateFor(request)?.await() ?: true
    }

    @Test
    fun cryptoIsSerializedAcrossConcurrentAuthorizedRequests() =
        runTest {
            val signGate = CompletableDeferred<Unit>()
            val signer = GatedSigner(userPubKey, signGate)
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, GatedAuthorizer { null })

            launch { processor.process(clientPubKey, signTemplate("1")) }
            launch { processor.process(clientPubKey, signTemplate("2")) }
            testScheduler.advanceUntilIdle()

            // Both were authorized instantly, but only one may be inside the signer at a time.
            assertEquals(1, signer.inFlight, "only one sign holds the crypto lock")
            assertEquals(1, signer.maxConcurrent, "crypto never overlapped")

            signGate.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(2, signer.signCount, "both eventually signed, one after the other")
            assertEquals(1, signer.maxConcurrent, "still never overlapped")
        }

    @Test
    fun aBlockedPromptDoesNotStallAnotherClientsSigning() =
        runTest {
            val signGate = CompletableDeferred<Unit>().apply { complete(Unit) } // signing itself never blocks here
            val signer = GatedSigner(userPubKey, signGate)
            val prompt = CompletableDeferred<Boolean>() // stands in for a user consent dialog left open
            val blocked = signTemplate("blocked")
            val processor =
                BunkerRequestProcessor(signer, { setOf(relay) }, GatedAuthorizer { if (it === blocked) prompt else null })

            launch { processor.process(clientPubKey, blocked) } // parks in authorize(), never touching the lock
            var fastResult: BunkerResponse? = null
            launch { fastResult = processor.process(clientPubKey, signTemplate("fast")) }
            testScheduler.advanceUntilIdle()

            // The auto-allowed request signed and returned while the prompt is still open.
            assertTrue(fastResult is BunkerResponseEvent, "auto-allowed request completed while a prompt was pending")
            assertEquals(1, signer.signCount)

            prompt.complete(true)
            testScheduler.advanceUntilIdle()
            assertEquals(2, signer.signCount, "the prompted request signs once approved")
        }
}
