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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseDecrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEncrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResponseParserTest {
    // --- ConnectResponse ---
    // connect never returns a pubkey — it's "ack" or the secret string.
    // Tests use base BunkerResponse to match production deserialization behavior.

    @Test
    fun connectParseAck() {
        val response = BunkerResponse("req-0", "ack", null)
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<ConnectResult>>(result)
        assertIs<ConnectResult.Ack>(result.result)
    }

    @Test
    fun connectParseSecret() {
        val response = BunkerResponse("req-0", "my-secret-token", null)
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<ConnectResult>>(result)
        assertIs<ConnectResult.Ack>(result.result)
    }

    @Test
    fun connectParseAlreadyConnected() {
        val response = BunkerResponse("req-0", null, "already connected")
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<ConnectResult>>(result)
        assertIs<ConnectResult.AlreadyConnected>(result.result)
    }

    @Test
    fun connectParseAlreadyConnectedCaseInsensitive() {
        val response = BunkerResponse("req-0", null, "Already Connected")
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<ConnectResult>>(result)
        assertIs<ConnectResult.AlreadyConnected>(result.result)
    }

    @Test
    fun connectParseRealError() {
        val response = BunkerResponse("req-0", null, "unauthorized")
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<ConnectResult>>(result)
    }

    @Test
    fun connectParseNoResultNoError() {
        val response = BunkerResponse("req-0", null, null)
        val result = ConnectResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<ConnectResult>>(result)
    }

    // --- PingResponse ---

    @Test
    fun pingParsePong() {
        val response = BunkerResponsePong("req-1")
        val result = PingResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<PingResult>>(result)
        assertEquals("req-1", result.result.pong)
    }

    @Test
    fun pingParseError() {
        val response = BunkerResponseError("req-1", "not allowed")
        val result = PingResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<PingResult>>(result)
    }

    @Test
    fun pingParseUnexpected() {
        val response = BunkerResponseAck("req-1")
        val result = PingResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<PingResult>>(result)
    }

    // --- PubKeyResponse ---

    @Test
    fun pubKeyParseSuccess() {
        val hex = "a".repeat(64)
        val response = BunkerResponsePublicKey("req-2", hex)
        val result = PubKeyResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<PublicKeyResult>>(result)
        assertEquals(hex, result.result.pubkey)
    }

    @Test
    fun pubKeyParseError() {
        val response = BunkerResponseError("req-2", "denied")
        val result = PubKeyResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<PublicKeyResult>>(result)
    }

    @Test
    fun pubKeyParseUnexpected() {
        val response = BunkerResponseAck("req-2")
        val result = PubKeyResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<PublicKeyResult>>(result)
    }

    // --- SignResponse ---

    @Test
    fun signParseValidEvent() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val event: Event =
                signer.sign(
                    createdAt = 1234L,
                    kind = 1,
                    tags = emptyArray(),
                    content = "hello",
                )
            val response = BunkerResponseEvent("req-3", event)
            val result = SignResponse.parse(response)
            assertIs<SignerResult.RequestAddressed.Successful<SignResult>>(result)
            assertEquals(event.id, result.result.event.id)
        }

    @Test
    fun signParseInvalidSignature() {
        val event =
            Event(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1234L,
                kind = 1,
                tags = emptyArray(),
                content = "hello",
                sig = "c".repeat(128),
            )
        val response = BunkerResponseEvent("req-3", event)
        val result = SignResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent<SignResult>>(result)
    }

    @Test
    fun signParseError() {
        val response = BunkerResponseError("req-3", "denied")
        val result = SignResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<SignResult>>(result)
    }

    @Test
    fun signParseUnexpected() {
        val response = BunkerResponseAck("req-3")
        val result = SignResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<SignResult>>(result)
    }

    // --- Nip04EncryptResponse ---

    @Test
    fun nip04EncryptParseSuccess() {
        val response = BunkerResponseEncrypt("req-4", "ciphertext-data")
        val result = Nip04EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<EncryptionResult>>(result)
        assertEquals("ciphertext-data", result.result.ciphertext)
    }

    @Test
    fun nip04EncryptParseError() {
        val response = BunkerResponseError("req-4", "fail")
        val result = Nip04EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<EncryptionResult>>(result)
    }

    @Test
    fun nip04EncryptParseUnexpectedNullResult() {
        val response = BunkerResponse("req-4", null, null)
        val result = Nip04EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<EncryptionResult>>(result)
    }

    @Test
    fun nip04EncryptParseGenericBunkerResponse() {
        // Production case: deserializer produces generic BunkerResponse with ciphertext
        val response = BunkerResponse("req-4", "encrypted-ciphertext-data", null)
        val result = Nip04EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<EncryptionResult>>(result)
        assertEquals("encrypted-ciphertext-data", result.result.ciphertext)
    }

    // --- Nip04DecryptResponse ---

    @Test
    fun nip04DecryptParseSuccess() {
        val response = BunkerResponseDecrypt("req-5", "plain-text")
        val result = Nip04DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<DecryptionResult>>(result)
        assertEquals("plain-text", result.result.plaintext)
    }

    @Test
    fun nip04DecryptParseError() {
        val response = BunkerResponseError("req-5", "fail")
        val result = Nip04DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<DecryptionResult>>(result)
    }

    @Test
    fun nip04DecryptParseUnexpectedNullResult() {
        val response = BunkerResponse("req-5", null, null)
        val result = Nip04DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<DecryptionResult>>(result)
    }

    @Test
    fun nip04DecryptParseGenericBunkerResponse() {
        // Production case: deserializer produces generic BunkerResponse with plaintext
        val response = BunkerResponse("req-5", "Hello, this is the decrypted message!", null)
        val result = Nip04DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<DecryptionResult>>(result)
        assertEquals("Hello, this is the decrypted message!", result.result.plaintext)
    }

    // --- Nip44EncryptResponse ---

    @Test
    fun nip44EncryptParseSuccess() {
        val response = BunkerResponseEncrypt("req-6", "nip44-cipher")
        val result = Nip44EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<EncryptionResult>>(result)
        assertEquals("nip44-cipher", result.result.ciphertext)
    }

    @Test
    fun nip44EncryptParseError() {
        val response = BunkerResponseError("req-6", "fail")
        val result = Nip44EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<EncryptionResult>>(result)
    }

    @Test
    fun nip44EncryptParseUnexpectedNullResult() {
        val response = BunkerResponse("req-6", null, null)
        val result = Nip44EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<EncryptionResult>>(result)
    }

    @Test
    fun nip44EncryptParseGenericBunkerResponse() {
        // Production case: deserializer produces generic BunkerResponse with ciphertext
        val response = BunkerResponse("req-6", "nip44-encrypted-payload", null)
        val result = Nip44EncryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<EncryptionResult>>(result)
        assertEquals("nip44-encrypted-payload", result.result.ciphertext)
    }

    // --- Nip44DecryptResponse ---

    @Test
    fun nip44DecryptParseSuccess() {
        val response = BunkerResponseDecrypt("req-7", "nip44-plain")
        val result = Nip44DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<DecryptionResult>>(result)
        assertEquals("nip44-plain", result.result.plaintext)
    }

    @Test
    fun nip44DecryptParseError() {
        val response = BunkerResponseError("req-7", "fail")
        val result = Nip44DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Rejected<DecryptionResult>>(result)
    }

    @Test
    fun nip44DecryptParseUnexpectedNullResult() {
        val response = BunkerResponse("req-7", null, null)
        val result = Nip44DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.ReceivedButCouldNotPerform<DecryptionResult>>(result)
    }

    @Test
    fun nip44DecryptParseGenericBunkerResponse() {
        // Production case: deserializer produces generic BunkerResponse with plaintext
        val response = BunkerResponse("req-7", "Decrypted NIP-44 content here", null)
        val result = Nip44DecryptResponse.parse(response)
        assertIs<SignerResult.RequestAddressed.Successful<DecryptionResult>>(result)
        assertEquals("Decrypted NIP-44 content here", result.result.plaintext)
    }
}
