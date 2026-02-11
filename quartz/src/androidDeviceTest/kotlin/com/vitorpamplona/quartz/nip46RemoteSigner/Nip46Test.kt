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
package com.vitorpamplona.quartz.nip46RemoteSigner

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class Nip46Test {
    val remoteKey = NostrSignerInternal(KeyPair())
    val signer = NostrSignerInternal(KeyPair())
    val peer = NostrSignerInternal(KeyPair())

    val dummyEvent =
        EventTemplate<Event>(
            createdAt = TimeUtils.now(),
            kind = 1,
            tags = emptyArray(),
            content = "test",
        )

    val dummyEventSigned =
        TextNoteEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "test",
            sig = "",
        )

    suspend fun <T : BunkerMessage> encodeDecodeEvent(req: T): T {
        val eventStr = NostrConnectEvent.create(req, remoteKey.pubKey, signer).toJson()

        return (Event.fromJson(eventStr) as NostrConnectEvent).decryptMessage(signer) as T
    }

    @Test
    fun signEncoder() =
        runTest {
            val expected = BunkerRequestSign(event = dummyEvent)
            val actual = encodeDecodeEvent(expected)

            assertEquals(BunkerRequestSign.METHOD_NAME, actual.method)
            assertEquals(expected.id, actual.id)
            assertEquals(dummyEvent.createdAt, actual.event.createdAt)
        }

    @Test
    fun connectEncoder() =
        runTest {
            val expected = BunkerRequestConnect(remoteKey = remoteKey.pubKey)
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
        }

    @Test
    fun pingEncoder() =
        runTest {
            val expected = BunkerRequestPing()
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
        }

    @Test
    fun getPubkeyEncoder() =
        runTest {
            val expected = BunkerRequestGetPublicKey()
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
        }

    @Test
    fun getRelaysEncoder() =
        runTest {
            val expected = BunkerRequestGetRelays()
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
        }

    @Test
    fun testNip04Encrypt() =
        runTest {
            val expected = BunkerRequestNip04Encrypt(pubKey = peer.pubKey, message = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
            assertEquals(expected.pubKey, actual.pubKey)
            assertEquals(expected.message, actual.message)
        }

    @Test
    fun testNip44Encrypt() =
        runTest {
            val expected = BunkerRequestNip44Encrypt(pubKey = peer.pubKey, message = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
            assertEquals(expected.pubKey, actual.pubKey)
            assertEquals(expected.message, actual.message)
        }

    @Test
    fun testNip04Decrypt() =
        runTest {
            val expected = BunkerRequestNip04Decrypt(pubKey = peer.pubKey, ciphertext = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
            assertEquals(expected.pubKey, actual.pubKey)
            assertEquals(expected.ciphertext, actual.ciphertext)
        }

    @Test
    fun testNip44Decrypt() =
        runTest {
            val expected = BunkerRequestNip44Decrypt(pubKey = peer.pubKey, ciphertext = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.method, actual.method)
            assertEquals(expected.id, actual.id)
            assertEquals(expected.pubKey, actual.pubKey)
            assertEquals(expected.ciphertext, actual.ciphertext)
        }

    // Responses

    @Test
    fun testAckResponse() =
        runTest {
            val expected = BunkerResponseAck()
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
        }

    @Test
    fun testPongResponse() =
        runTest {
            val expected = BunkerResponsePong()
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
        }

    @Test
    fun testErrorResponse() =
        runTest {
            val expected = BunkerResponseError(error = "Error")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
        }

    @Test
    fun testEventResponse() =
        runTest {
            val expected = BunkerResponseEvent(event = dummyEventSigned)
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
            assertEquals(dummyEventSigned.id, actual.event.id)
        }

    @Test
    fun testPubkeyResponse() =
        runTest {
            val expected = BunkerResponsePublicKey(pubkey = peer.pubKey)
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
            assertEquals(expected.pubkey, actual.pubkey)
        }

    @Test
    fun testRelaysResponse() =
        runTest {
            val expected = BunkerResponseGetRelays(relays = mapOf("url" to ReadWrite(true, false)))
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
            assertEquals(expected.relays["url"], actual.relays["url"])
        }

    @Test
    @Ignore("Impossible to recreate the class since there are no hints on the json")
    fun testDecryptResponse() =
        runTest {
            val expected = BunkerResponseDecrypt(plaintext = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
            assertEquals(expected.plaintext, actual.plaintext)
        }

    @Test
    @Ignore("Impossible to recreate the class since there are no hints on the json")
    fun testEncryptResponse() =
        runTest {
            val expected = BunkerResponseEncrypt(ciphertext = "Test")
            val actual = encodeDecodeEvent(expected)

            assertEquals(expected.id, actual.id)
            assertEquals(expected.result, actual.result)
            assertEquals(expected.error, actual.error)
            assertEquals(expected.ciphertext, actual.ciphertext)
        }
}
