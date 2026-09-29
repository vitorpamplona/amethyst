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

import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.framing.MlsMessage
import com.vitorpamplona.quartz.mls.framing.PublicMessage
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.group.MlsGroupState
import com.vitorpamplona.quartz.mls.schedule.KeySchedule
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A member that sent before seeing the latest commit sealed its message in a
 * former epoch. The receiver keeps the last few epochs' receiver data and
 * opens such a message with [MlsGroup.decryptFormerEpoch].
 */
class MlsGroupFormerEpochTest {
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
    fun aLateMessageOpensFromTheFormerEpoch() {
        val (alice, bob) = twoMembers()
        val formerEpoch = alice.epoch
        val formerExporter = alice.exporterSecret("test", ByteArray(0), 32)
        val late = bob.encrypt("sent before the commit".encodeToByteArray(), "aad".encodeToByteArray())
        alice.commit() // bob sent before seeing this

        assertEquals(formerEpoch + 1, alice.epoch)
        assertTrue(runCatching { alice.decrypt(late) }.isFailure)

        val opened = alice.decryptFormerEpoch(late)
        assertContentEquals("sent before the commit".encodeToByteArray(), opened.content)
        assertContentEquals("aad".encodeToByteArray(), opened.authenticatedData)
        assertEquals(bob.leafIndex, opened.senderLeafIndex)
        assertEquals(formerEpoch, opened.epoch)

        val retainedExporter = alice.formerExporterSecrets().getValue(formerEpoch)
        assertContentEquals(
            formerExporter,
            KeySchedule.mlsExporter(retainedExporter, "test", ByteArray(0), 32),
        )
    }

    @Test
    fun aFormerEpochMessageOpensOnlyOnce() {
        val (alice, bob) = twoMembers()
        val late = bob.encrypt("once".encodeToByteArray())
        alice.commit() // bob sent before seeing this

        alice.decryptFormerEpoch(late)
        assertFailsWith<IllegalArgumentException> { alice.decryptFormerEpoch(late) }
    }

    @Test
    fun outOfOrderLateMessagesAllOpen() {
        val (alice, bob) = twoMembers()
        val first = bob.encrypt("first".encodeToByteArray())
        val second = bob.encrypt("second".encodeToByteArray())
        alice.commit() // bob sent before seeing this

        assertContentEquals("second".encodeToByteArray(), alice.decryptFormerEpoch(second).content)
        assertContentEquals("first".encodeToByteArray(), alice.decryptFormerEpoch(first).content)
    }

    @Test
    fun theWindowSurvivesARestore() {
        val (alice, bob) = twoMembers()
        val first = bob.encrypt("first".encodeToByteArray())
        val second = bob.encrypt("second".encodeToByteArray())
        alice.commit() // bob sent before seeing this
        alice.decryptFormerEpoch(second)

        val restored = alice.saveAndRestore()
        assertEquals(alice.retainedEpochs().map { it.epoch }, restored.retainedEpochs().map { it.epoch })
        assertContentEquals("first".encodeToByteArray(), restored.decryptFormerEpoch(first).content)
        // what was opened before the restore stays opened
        assertTrue(runCatching { restored.decryptFormerEpoch(second) }.isFailure)
    }

    @Test
    fun onlyTheLastEpochsAreKept() {
        val (alice, bob) = twoMembers()
        val tooLate = bob.encrypt("too late".encodeToByteArray())
        val epochOfTooLate = alice.epoch
        repeat(MlsGroup.RETAIN_EPOCHS + 1) { alice.commit() }

        assertEquals(MlsGroup.RETAIN_EPOCHS, alice.retainedEpochs().size)
        assertTrue(alice.retainedEpochs().none { it.epoch == epochOfTooLate })
        val e = assertFailsWith<IllegalArgumentException> { alice.decryptFormerEpoch(tooLate) }
        assertTrue(e.message!!.contains("no longer retained"))
    }

    @Test
    fun aCurrentEpochMessageIsNotAFormerOne() {
        val (alice, bob) = twoMembers()
        val current = bob.encrypt("now".encodeToByteArray())
        assertFailsWith<IllegalArgumentException> { alice.decryptFormerEpoch(current) }
        assertContentEquals("now".encodeToByteArray(), alice.decrypt(current).content)
    }

    @Test
    fun aRejectedCommitLeavesTheWindowAlone() {
        val (alice, bob) = twoMembers()
        val commit = bob.commit().framedCommitBytes
        val pub = PublicMessage.decodeTls(TlsReader(MlsMessage.decodeTls(TlsReader(commit)).payload))
        val badTag = pub.confirmationTag!!.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val before = alice.retainedEpochs().map { it.epoch }

        // fails on the confirmation tag, after the new epoch was derived
        assertTrue(runCatching { alice.processCommit(pub.content, pub.sender.leafIndex, badTag, pub.signature) }.isFailure)
        assertEquals(before, alice.retainedEpochs().map { it.epoch })
        alice.processFramedCommit(commit)
        assertEquals(before + (bob.epoch - 1), alice.retainedEpochs().map { it.epoch })
    }
}
