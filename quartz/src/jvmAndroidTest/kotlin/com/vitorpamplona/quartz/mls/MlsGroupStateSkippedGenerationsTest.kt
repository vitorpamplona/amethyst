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
package com.vitorpamplona.quartz.mls

import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.group.MlsGroupState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A message that arrives after a later one from the same sender is opened
 * with the secret its generation left behind. Those secrets are part of the
 * saved state, so a restart between the two messages does not lose the
 * earlier one.
 */
class MlsGroupStateSkippedGenerationsTest {
    private fun twoMembers(): Pair<MlsGroup, MlsGroup> {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle =
            MlsGroup
                .create("bob".encodeToByteArray())
                .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)
        return alice to bob
    }

    private fun MlsGroup.saveAndRestore() = MlsGroup.restore(MlsGroupState.decodeTls(saveState().encodeTls()))

    @Test
    fun skippedMessageOpensAfterRestore() {
        val (alice, bob) = twoMembers()
        val ct0 = alice.encrypt("msg0".encodeToByteArray())
        val ct1 = alice.encrypt("msg1".encodeToByteArray())
        val ct2 = alice.encrypt("msg2".encodeToByteArray())

        // generation 2 first: bob's ratchet moves past 0 and 1
        assertContentEquals("msg2".encodeToByteArray(), bob.decrypt(ct2).content)
        assertEquals(2, bob.saveState().skippedApplicationSecrets.size)

        val bobRestored = bob.saveAndRestore()
        assertContentEquals("msg0".encodeToByteArray(), bobRestored.decrypt(ct0).content)
        assertContentEquals("msg1".encodeToByteArray(), bobRestored.decrypt(ct1).content)
        assertTrue(bobRestored.saveState().skippedApplicationSecrets.isEmpty())
    }

    @Test
    fun anOpenedSkippedGenerationIsNotPersisted() {
        val (alice, bob) = twoMembers()
        val ct0 = alice.encrypt("msg0".encodeToByteArray())
        val ct1 = alice.encrypt("msg1".encodeToByteArray())

        bob.decrypt(ct1)
        bob.decrypt(ct0)

        val bobRestored = bob.saveAndRestore()
        assertTrue(bobRestored.saveState().skippedApplicationSecrets.isEmpty())
        assertTrue(runCatching { bobRestored.decrypt(ct0) }.isFailure)
    }

    @Test
    fun version4StateStillDecodes() {
        val (_, bob) = twoMembers()
        val v5 = bob.saveState().encodeTls()
        // with nothing skipped, v5 is v4 plus two empty counts (uint32 each)
        val v4 = v5.copyOfRange(0, v5.size - 8)
        v4[0] = 0
        v4[1] = 4

        val restored = MlsGroup.restore(MlsGroupState.decodeTls(v4))
        assertEquals(bob.epoch, restored.epoch)
        assertTrue(restored.saveState().skippedApplicationSecrets.isEmpty())
    }
}
