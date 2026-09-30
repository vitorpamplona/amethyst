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
 * A saved state without the secret tree's root, as another implementation
 * (ts-mls) keeps it, restores from the unexpanded node secrets alone.
 */
class MlsGroupStateNodeSecretsTest {
    private fun twoMembers(): Pair<MlsGroup, MlsGroup> {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle =
            MlsGroup
                .create("bob".encodeToByteArray())
                .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)
        return alice to bob
    }

    @Test
    fun aStateWithoutTheRootSecretRestores() {
        val (alice, bob) = twoMembers()
        // bob derives alice's leaf, so his tree no longer needs the root
        assertContentEquals("hi".encodeToByteArray(), bob.decrypt(alice.encrypt("hi".encodeToByteArray())).content)

        val saved = bob.saveState()
        assertTrue(saved.nodeSecrets.isNotEmpty())
        val rootless = saved.copy(encryptionSecret = ByteArray(0))
        val restored = MlsGroup.restore(MlsGroupState.decodeTls(rootless.encodeTls()))

        // bob's own leaf comes from the kept sibling, and alice can read it
        assertContentEquals("from bob".encodeToByteArray(), alice.decrypt(restored.encrypt("from bob".encodeToByteArray())).content)
        // alice's ratchet continues where it was
        assertContentEquals("again".encodeToByteArray(), restored.decrypt(alice.encrypt("again".encodeToByteArray())).content)
    }

    @Test
    fun retainedEpochsKeepTheirNodeSecrets() {
        val (alice, bob) = twoMembers()
        val late = bob.encrypt("late".encodeToByteArray())
        alice.decrypt(bob.encrypt("seen".encodeToByteArray()))
        alice.commit()

        val retained = alice.retainedEpochs().last()
        assertTrue(retained.nodeSecrets.isNotEmpty())
        val restored = MlsGroup.restore(MlsGroupState.decodeTls(alice.saveState().encodeTls()))
        assertEquals(
            retained.nodeSecrets.keys,
            restored
                .retainedEpochs()
                .last()
                .nodeSecrets.keys,
        )
        assertContentEquals("late".encodeToByteArray(), restored.decryptFormerEpoch(late).content)
    }

    @Test
    fun version6StateStillDecodes() {
        val (_, bob) = twoMembers()
        val state = bob.saveState()
        val v7 = state.copy(nodeSecrets = emptyMap()).encodeTls()
        // with no retained epochs and no node secrets, v7 is v6 plus one empty count (uint32)
        assertTrue(state.retainedEpochs.isEmpty())
        val v6 = v7.copyOfRange(0, v7.size - 4)
        v6[0] = 0
        v6[1] = 6

        val restored = MlsGroup.restore(MlsGroupState.decodeTls(v6))
        assertEquals(bob.epoch, restored.epoch)
    }
}
