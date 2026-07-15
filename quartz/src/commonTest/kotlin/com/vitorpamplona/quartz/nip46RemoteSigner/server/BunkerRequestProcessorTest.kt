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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEncrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure dispatch/authorization tests for the NIP-46 signer core. Uses a canned
 * [FakeSigner] so no secp256k1/NIP-44 crypto is required — these run in
 * commonTest on every platform.
 */
class BunkerRequestProcessorTest {
    private val userPubKey = "a".repeat(64)
    private val clientPubKey = "c".repeat(64)

    /** Records what the signer was asked to do and returns fixed values. */
    private class FakeSigner(
        pubKey: HexKey,
    ) : NostrSigner(pubKey) {
        var signCount = 0
        var nip44EncryptCount = 0
        var nip44DecryptCount = 0

        val cannedEvent =
            Event(
                id = "e".repeat(64),
                pubKey = pubKey,
                createdAt = 1L,
                kind = 1,
                tags = emptyArray(),
                content = "signed",
                sig = "f".repeat(128),
            )

        override fun isWriteable() = true

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T {
            signCount++
            return cannedEvent as T
        }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = "nip04:$plaintext"

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = "nip04dec:$ciphertext"

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String {
            nip44EncryptCount++
            return "nip44:$plaintext"
        }

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String {
            nip44DecryptCount++
            return "nip44dec:$ciphertext"
        }

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = throw NotImplementedError()

        override suspend fun deriveKey(nonce: HexKey): HexKey = throw NotImplementedError()

        override suspend fun signPsbt(psbtHex: String): String = throw NotImplementedError()

        override fun hasForegroundSupport() = false
    }

    /** Configurable authorizer for the tests. */
    private class FakeAuthorizer(
        val connectDecision: Nip46ConnectDecision,
        val allow: Boolean,
    ) : Nip46RequestAuthorizer {
        var connectCalls = 0
        var authorizeCalls = 0

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ): Nip46ConnectDecision {
            connectCalls++
            return connectDecision
        }

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ): Boolean {
            authorizeCalls++
            return allow
        }
    }

    private val relay: NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun processor(
        signer: FakeSigner = FakeSigner(userPubKey),
        authorizer: FakeAuthorizer = FakeAuthorizer(Nip46ConnectDecision.Accept("ack"), allow = true),
    ) = BunkerRequestProcessor(signer, { setOf(relay) }, authorizer)

    @Test
    fun getPublicKeyReturnsUserPubKeyWithoutAuthorization() =
        runTest {
            val authorizer = FakeAuthorizer(Nip46ConnectDecision.Accept("ack"), allow = false)
            val res = processor(authorizer = authorizer).process(clientPubKey, BunkerRequestGetPublicKey("1"))

            assertTrue(res is BunkerResponsePublicKey)
            assertEquals(userPubKey, res.pubkey)
            assertEquals("1", res.id)
            // public reads are never gated
            assertEquals(0, authorizer.authorizeCalls)
        }

    @Test
    fun pingReturnsPong() =
        runTest {
            val res = processor().process(clientPubKey, BunkerRequestPing("2"))
            assertTrue(res is BunkerResponsePong)
            assertEquals("2", res.id)
        }

    @Test
    fun getRelaysReturnsConfiguredRelays() =
        runTest {
            val res = processor().process(clientPubKey, BunkerRequestGetRelays("3"))
            assertTrue(res is BunkerResponseGetRelays)
            assertTrue(res.relays.containsKey(relay.url))
        }

    @Test
    fun connectAcceptEchoesSecret() =
        runTest {
            val authorizer = FakeAuthorizer(Nip46ConnectDecision.Accept("s3cr3t"), allow = true)
            val res = processor(authorizer = authorizer).process(clientPubKey, BunkerRequestConnect(id = "4", remoteKey = userPubKey, secret = "s3cr3t"))

            assertEquals(1, authorizer.connectCalls)
            assertEquals("4", res.id)
            assertEquals("s3cr3t", res.result)
        }

    @Test
    fun connectRejectReturnsError() =
        runTest {
            val authorizer = FakeAuthorizer(Nip46ConnectDecision.Reject("invalid secret"), allow = true)
            val res = processor(authorizer = authorizer).process(clientPubKey, BunkerRequestConnect(id = "5", remoteKey = userPubKey, secret = "wrong"))

            assertTrue(res is BunkerResponseError)
            assertEquals("invalid secret", res.error)
        }

    @Test
    fun signAuthorizedSignsWithUserSigner() =
        runTest {
            val signer = FakeSigner(userPubKey)
            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hi")
            val res = processor(signer = signer).process(clientPubKey, BunkerRequestSign("6", template))

            assertTrue(res is BunkerResponseEvent)
            assertEquals(1, signer.signCount)
            assertEquals(signer.cannedEvent.id, res.event.id)
        }

    @Test
    fun signDeniedReturnsUnauthorized() =
        runTest {
            val signer = FakeSigner(userPubKey)
            val authorizer = FakeAuthorizer(Nip46ConnectDecision.Accept("ack"), allow = false)
            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hi")
            val res = processor(signer = signer, authorizer = authorizer).process(clientPubKey, BunkerRequestSign("7", template))

            assertTrue(res is BunkerResponseError)
            assertEquals(BunkerRequestProcessor.ERROR_UNAUTHORIZED, res.error)
            assertEquals(0, signer.signCount)
        }

    @Test
    fun nip44EncryptAuthorized() =
        runTest {
            val signer = FakeSigner(userPubKey)
            val res = processor(signer = signer).process(clientPubKey, BunkerRequestNip44Encrypt("8", clientPubKey, "hello"))

            assertTrue(res is BunkerResponseEncrypt)
            assertEquals("nip44:hello", res.ciphertext)
            assertEquals(1, signer.nip44EncryptCount)
        }

    @Test
    fun nip44DecryptDeniedDoesNotCallSigner() =
        runTest {
            val signer = FakeSigner(userPubKey)
            val authorizer = FakeAuthorizer(Nip46ConnectDecision.Accept("ack"), allow = false)
            val res = processor(signer = signer, authorizer = authorizer).process(clientPubKey, BunkerRequestNip44Decrypt("9", clientPubKey, "ct"))

            assertTrue(res is BunkerResponseError)
            assertEquals(0, signer.nip44DecryptCount)
        }

    @Test
    fun unsupportedMethodReturnsError() =
        runTest {
            val res = processor().process(clientPubKey, BunkerRequest("10", "made_up_method", emptyArray()))
            assertTrue(res is BunkerResponseError)
            assertTrue(res.error!!.contains("made_up_method"))
        }

    @Test
    fun signerExceptionBecomesErrorResponse() =
        runTest {
            val throwing =
                object : NostrSigner(userPubKey) {
                    override fun isWriteable() = true

                    override suspend fun <T : Event> sign(
                        createdAt: Long,
                        kind: Int,
                        tags: Array<Array<String>>,
                        content: String,
                    ): T = throw IllegalStateException("boom")

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
            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hi")
            val res =
                BunkerRequestProcessor(throwing, { setOf(relay) }, FakeAuthorizer(Nip46ConnectDecision.Accept("ack"), allow = true))
                    .process(clientPubKey, BunkerRequestSign("11", template))

            assertTrue(res is BunkerResponseError)
            assertTrue(res.error!!.contains("boom"))
        }
}
