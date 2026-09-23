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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** `authenticated_data` on application messages (RFC 9420 §6.3.2): sent in the clear, bound by the AEAD. */
class AuthenticatedDataTest {
    private val aad = "sender-binding".encodeToByteArray()

    @Test
    fun theReceiverGetsTheSendersAuthenticatedData() {
        val (alice, bob) = twoMemberGroup()

        val received = bob.decrypt(alice.encrypt("hi".encodeToByteArray(), aad))

        assertContentEquals("hi".encodeToByteArray(), received.content)
        assertContentEquals(aad, received.authenticatedData)
    }

    @Test
    fun withoutItTheAuthenticatedDataIsEmpty() {
        val (alice, bob) = twoMemberGroup()

        val received = bob.decrypt(alice.encrypt("hi".encodeToByteArray()))

        assertEquals(0, received.authenticatedData.size)
    }

    @Test
    fun alteredAuthenticatedDataFailsToDecrypt() {
        val (alice, bob) = twoMemberGroup()

        val sent = MlsMessage.decodeTls(TlsReader(alice.encrypt("hi".encodeToByteArray(), aad)))
        val message = PrivateMessage.decodeTls(TlsReader(sent.payload))
        val altered = MlsMessage.fromPrivateMessage(message.copy(authenticatedData = "someone-else".encodeToByteArray()))

        assertFails { bob.decrypt(altered.toTlsBytes()) }
    }

    private fun twoMemberGroup(): Pair<MlsGroup, MlsGroup> {
        val alice = MlsGroup.create("11".repeat(32).hexToByteArray())
        val bobBundle = alice.createKeyPackage("22".repeat(32).hexToByteArray(), ByteArray(0))
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)
        return alice to bob
    }
}
