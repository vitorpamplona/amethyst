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
package com.vitorpamplona.contextvm.crypto

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.contextvm.core.CvmMessageEvent
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `CVM-4-*`: the gift wrap round trip against real NIP-44 and secp256k1.
 *
 * Lives in jvmTest rather than commonTest because it needs the secp256k1 JNI.
 */
class CvmGiftWrapCryptoTest {
    private val clientSigner = NostrSignerInternal(KeyPair())
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val crypto = CvmGiftWrap()

    private suspend fun innerMessage(): Event =
        CvmMessageEvent.create(
            message = JsonRpcRequest(JsonRpcId.Num(1), "tools/list"),
            recipient = serverSigner.pubKey,
            signer = clientSigner,
        )

    @Test
    fun `CVM-4-10 round-trips a wrapped message and recovers the signed inner event`() =
        runTest {
            val inner = innerMessage()
            val wrap = crypto.wrap(inner, serverSigner.pubKey)

            assertEquals(CvmKinds.EPHEMERAL_GIFT_WRAP, wrap.kind)

            val unwrapped = crypto.unwrap(wrap, serverSigner)
            assertEquals(inner.id, unwrapped.id)
            assertEquals(inner.pubKey, unwrapped.pubKey)
            assertEquals(inner.content, unwrapped.content)
            assertEquals(CvmKinds.MESSAGE, unwrapped.kind)
        }

    @Test
    fun `CVM-4-11 the inner event is signed by the real client, not the wrap key`() =
        runTest {
            // CEP-4 signs the inner event first and encrypts the whole signed
            // event. The wrap's own signature only proves the throwaway key
            // signed it, so authorship comes from the inner one.
            val inner = innerMessage()
            val wrap = crypto.wrap(inner, serverSigner.pubKey)

            assertEquals(clientSigner.pubKey, crypto.unwrap(wrap, serverSigner).pubKey)
            assertNotEquals(clientSigner.pubKey, wrap.pubKey, "the wrap must not be signed by the client key")
        }

    @Test
    fun `CVM-4-12 each wrap uses a fresh throwaway key`() =
        runTest {
            val inner = innerMessage()
            val first = crypto.wrap(inner, serverSigner.pubKey)
            val second = crypto.wrap(inner, serverSigner.pubKey)

            assertNotEquals(
                first.pubKey,
                second.pubKey,
                "reusing a wrap key would link two messages from the same sender",
            )
        }

    @Test
    fun `CVM-4-13 the wrap addresses the recipient with a p tag`() =
        runTest {
            val wrap = crypto.wrap(innerMessage(), serverSigner.pubKey)
            val pTag = wrap.tags.first { it[0] == "p" }
            assertEquals(serverSigner.pubKey, pTag[1])
        }

    @Test
    fun `CVM-4-14 the wrap timestamp is shifted and must not be used for ordering`() =
        runTest {
            val wrap = crypto.wrap(innerMessage(), serverSigner.pubKey)
            val now =
                com.vitorpamplona.quartz.utils.TimeUtils
                    .now()
            assertTrue(wrap.createdAt <= now, "NIP-59 shifts the timestamp into the past")
            assertTrue(wrap.createdAt > now - (3 * 24 * 60 * 60), "but only within a bounded window")
        }

    @Test
    fun `CVM-4-15 rejects an inner event whose signature does not verify`() =
        runTest {
            // Without this check, anyone able to encrypt to us could claim any
            // pubkey: the wrap signature proves nothing about the inner author.
            val inner = innerMessage()
            val forged =
                Event(
                    id = inner.id,
                    pubKey = inner.pubKey,
                    createdAt = inner.createdAt,
                    kind = inner.kind,
                    tags = inner.tags,
                    content = inner.content,
                    sig = "00".repeat(64),
                )
            val wrap = crypto.wrap(forged, serverSigner.pubKey)

            assertFailsWith<CvmEncryptionException> { crypto.unwrap(wrap, serverSigner) }
        }

    @Test
    fun `CVM-4-16 rejects a wrap addressed to someone else`() =
        runTest {
            val wrap = crypto.wrap(innerMessage(), serverSigner.pubKey)
            val eavesdropper = NostrSignerInternal(KeyPair())
            assertFailsWith<CvmEncryptionException> { crypto.unwrap(wrap, eavesdropper) }
        }

    @Test
    fun `CVM-4-17 rejects a non-gift-wrap kind on both paths`() =
        runTest {
            val inner = innerMessage()
            assertFailsWith<CvmEncryptionException> { crypto.wrap(inner, serverSigner.pubKey, kind = 1) }
            assertFailsWith<CvmEncryptionException> { crypto.unwrap(inner, serverSigner) }
        }

    @Test
    fun `CVM-19-10 a persistent-mode client produces kind 1059`() =
        runTest {
            val persistent = CvmGiftWrap(giftWrapMode = GiftWrapMode.PERSISTENT)
            val wrap = persistent.wrap(innerMessage(), serverSigner.pubKey)
            assertEquals(CvmKinds.GIFT_WRAP, wrap.kind)

            // Either kind must unwrap regardless of which mode we prefer.
            assertEquals(CvmKinds.MESSAGE, crypto.unwrap(wrap, serverSigner).kind)
        }
}
