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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletRelayCleartext
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NappletRelayCleartextTest {
    @Test
    fun plaintextPassesThroughWithoutDecrypting() =
        runTest {
            val event = event(content = "hello")
            val result =
                NappletRelayCleartext.forDelivery(event, USER) { _, _ ->
                    error("plaintext must not be decrypted")
                }

            assertSame(event, result)
        }

    @Test
    fun inboundNip04IsProjectedAsCleartext() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", USER)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER) { ciphertext, peer ->
                    assertEquals(NIP04, ciphertext)
                    assertEquals(AUTHOR, peer)
                    "secret"
                }

            assertEquals("secret", result?.content)
            assertEquals(event.id, result?.id)
            assertEquals(event.sig, result?.sig)
        }

    @Test
    fun outboundEncryptedEventUsesItsRecipientAsPeer() =
        runTest {
            val event = event(pubKey = USER, content = NIP04, tags = arrayOf(arrayOf("p", RECIPIENT)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER) { _, peer ->
                    assertEquals(RECIPIENT, peer)
                    "sent secret"
                }

            assertEquals("sent secret", result?.content)
        }

    @Test
    fun encryptedEventForAnotherUserIsWithheld() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", RECIPIENT)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER) { _, _ ->
                    error("unrelated ciphertext must not be offered to the signer")
                }

            assertNull(result)
        }

    @Test
    fun decryptionFailureWithholdsCiphertext() =
        runTest {
            val event = event(content = NIP04, tags = arrayOf(arrayOf("p", USER)))
            val result =
                NappletRelayCleartext.forDelivery(event, USER) { _, _ ->
                    error("signer refused")
                }

            assertNull(result)
        }

    private fun event(
        pubKey: String = AUTHOR,
        content: String,
        tags: Array<Array<String>> = emptyArray(),
    ) = Event("id", pubKey, 1L, 4, tags, content, "sig")

    companion object {
        private const val USER = "user"
        private const val AUTHOR = "author"
        private const val RECIPIENT = "recipient"
        private const val NIP04 = "ciphertext-that-is-long-enough?iv=123456789012345678901234"
    }
}
