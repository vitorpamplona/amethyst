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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case and error handling tests for MlsGroup.
 *
 * Tests security-critical boundaries:
 * - Wrong epoch messages are rejected
 * - Corrupted ciphertext is detected (AEAD authentication)
 * - Invalid KeyPackages are rejected
 * - Out-of-range leaf indices are caught
 * - Self-removal via Remove (not SelfRemove) is rejected
 * - Empty messages and large messages are handled correctly
 * - DecryptOrNull returns null on failure instead of throwing
 */
class MlsGroupEdgeCaseTest {
    private fun createStandaloneKeyPackage(identity: String): KeyPackageBundle {
        val tempGroup = MlsGroup.create(identity.encodeToByteArray())
        return tempGroup.createKeyPackage(identity.encodeToByteArray(), ByteArray(0))
    }

    // -----------------------------------------------------------------------
    // 1. Wrong epoch rejection
    // -----------------------------------------------------------------------

    @Test
    fun testDecryptRejectsWrongEpoch() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Alice encrypts at epoch 1
        val ct = alice.encrypt("epoch 1 message".encodeToByteArray())

        // Advance Bob to epoch 2 by having him commit (empty commit)
        bob.commit()
        assertEquals(2L, bob.epoch)

        // Bob's epoch is now 2, but the message was at epoch 1 — should fail
        assertFailsWith<IllegalArgumentException>("Decrypting wrong-epoch message should throw") {
            bob.decrypt(ct)
        }
    }

    @Test
    fun testDecryptOrNullReturnsNullOnWrongEpoch() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val ct = alice.encrypt("epoch 1 message".encodeToByteArray())

        bob.commit()

        val result = bob.decryptOrNull(ct)
        assertNull(result, "decryptOrNull should return null for wrong-epoch message")
    }

    // -----------------------------------------------------------------------
    // 2. Corrupted ciphertext detection
    // -----------------------------------------------------------------------

    @Test
    fun testDecryptRejectsTamperedCiphertext() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val ct = alice.encrypt("secret message".encodeToByteArray())

        // Tamper with the ciphertext (flip a byte near the end)
        val tampered = ct.copyOf()
        if (tampered.size > 10) {
            tampered[tampered.size - 5] = (tampered[tampered.size - 5].toInt() xor 0xFF).toByte()
        }

        // AEAD should detect tampering
        assertNull(alice.decryptOrNull(tampered), "Tampered ciphertext should fail decryption")
    }

    @Test
    fun testDecryptRejectsTruncatedMessage() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val ct = alice.encrypt("test".encodeToByteArray())

        // Truncate to half length
        val truncated = ct.copyOfRange(0, ct.size / 2)
        assertNull(alice.decryptOrNull(truncated), "Truncated message should fail")
    }

    @Test
    fun testDecryptRejectsGarbageInput() {
        val alice = MlsGroup.create("alice".encodeToByteArray())

        val garbage = ByteArray(100) { it.toByte() }
        assertNull(alice.decryptOrNull(garbage), "Garbage input should fail gracefully")
    }

    // -----------------------------------------------------------------------
    // 3. Invalid KeyPackage rejection
    // -----------------------------------------------------------------------

    @Test
    fun testAddMemberRejectsInvalidKeyPackageSignature() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val kpBytes = bobBundle.keyPackage.toTlsBytes()

        // Tamper with the signature in the serialized KeyPackage
        val tampered = kpBytes.copyOf()
        // The signature is at the end of the TLS-serialized KeyPackage
        if (tampered.size > 10) {
            tampered[tampered.size - 3] = (tampered[tampered.size - 3].toInt() xor 0xFF).toByte()
        }

        assertFailsWith<IllegalArgumentException>("Adding member with invalid KeyPackage signature should fail") {
            alice.addMember(tampered)
        }
    }

    // -----------------------------------------------------------------------
    // 4. Out-of-range leaf index rejection
    // -----------------------------------------------------------------------

    @Test
    fun testRemoveRejectsOutOfRangeLeafIndex() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Leaf index 99 is way out of range
        assertFailsWith<IllegalArgumentException>("Removing out-of-range leaf should fail") {
            alice.removeMember(99)
        }
    }

    @Test
    fun testRemoveRejectsBlankLeaf() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Remove Bob (leaf 1)
        alice.removeMember(1)

        // Try to remove leaf 1 again (now blank)
        assertFailsWith<IllegalArgumentException>("Removing blank leaf should fail") {
            alice.removeMember(1)
        }
    }

    @Test
    fun testRemoveRejectsSelfRemovalViaRemove() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Alice tries to remove herself via Remove (should use SelfRemove instead)
        assertFailsWith<IllegalArgumentException>("Self-removal via Remove should be rejected") {
            alice.removeMember(alice.leafIndex)
        }
    }

    // -----------------------------------------------------------------------
    // 5. Empty and large messages
    // -----------------------------------------------------------------------

    @Test
    fun testEncryptDecryptEmptyMessage() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val empty = ByteArray(0)
        val ct = alice.encrypt(empty)
        val dec = bob.decrypt(ct)
        assertContentEquals(empty, dec.content, "Empty message should round-trip")
    }

    @Test
    fun testEncryptDecryptLargeMessage() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // 64KB message
        val large = ByteArray(65536) { (it % 256).toByte() }
        val ct = alice.encrypt(large)
        val dec = bob.decrypt(ct)
        assertContentEquals(large, dec.content, "Large message should round-trip")
    }

    // -----------------------------------------------------------------------
    // 6. Multiple epochs of encrypt/decrypt
    // -----------------------------------------------------------------------

    @Test
    fun testMultipleEpochTransitions_EncryptDecryptStillWorks() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Advance through several epochs with empty commits
        for (i in 0 until 5) {
            val commitResult = alice.commit()
            bob.processCommit(commitResult.commitBytes, alice.leafIndex, ByteArray(0))
        }

        // epoch 0 (create) -> epoch 1 (add bob) -> 5 empty commits = epoch 6
        assertEquals(6L, alice.epoch)
        assertEquals(alice.epoch, bob.epoch)

        // Both directions still work
        val msg = "After many epochs".encodeToByteArray()
        val ct = alice.encrypt(msg)
        val dec = bob.decrypt(ct)
        assertContentEquals(msg, dec.content)

        val msg2 = "Bob replies after epochs".encodeToByteArray()
        val ct2 = bob.encrypt(msg2)
        val dec2 = alice.decrypt(ct2)
        assertContentEquals(msg2, dec2.content)
    }

    // -----------------------------------------------------------------------
    // 7. Exporter secret uniqueness across epochs
    // -----------------------------------------------------------------------

    @Test
    fun testExporterSecretUniquePerEpoch() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val keys = mutableListOf<ByteArray>()

        // Collect exporter secrets across several epochs
        keys.add(alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32))

        for (i in 0 until 3) {
            val commitResult = alice.commit()
            bob.processCommit(commitResult.commitBytes, alice.leafIndex, ByteArray(0))
            keys.add(alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32))
        }

        // All keys should be distinct
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                assertFalse(
                    keys[i].contentEquals(keys[j]),
                    "Exporter secrets at epoch $i and $j must differ",
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 8. Welcome with wrong KeyPackageBundle is rejected
    // -----------------------------------------------------------------------

    @Test
    fun testWelcomeRejectsWrongKeyPackageBundle() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Carol's bundle (not the one Alice invited)
        val carolBundle = createStandaloneKeyPackage("carol")

        // Processing Welcome with wrong bundle should fail
        assertFailsWith<IllegalArgumentException>("Welcome with wrong KeyPackage should be rejected") {
            MlsGroup.processWelcome(result.welcomeBytes!!, carolBundle)
        }
    }

    // -----------------------------------------------------------------------
    // 9. Group state after multiple add/remove cycles
    // -----------------------------------------------------------------------

    @Test
    fun testAddRemoveAddCycle_GroupRemainsConsistent() {
        val alice = MlsGroup.create("alice".encodeToByteArray())

        // Add Bob
        val bobBundle = createStandaloneKeyPackage("bob")
        val addBob = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        assertEquals(2, alice.memberCount)

        // Remove Bob
        alice.removeMember(1)
        assertEquals(1, alice.memberCount)

        // Add Carol (she should occupy a leaf slot)
        val carolBundle = createStandaloneKeyPackage("carol")
        val addCarol = alice.addMember(carolBundle.keyPackage.toTlsBytes())
        assertEquals(2, alice.memberCount)

        // Carol joins and can communicate with Alice
        val carol = MlsGroup.processWelcome(addCarol.welcomeBytes!!, carolBundle)
        val msg = "After add-remove-add cycle".encodeToByteArray()
        val ct = alice.encrypt(msg)
        val dec = carol.decrypt(ct)
        assertContentEquals(msg, dec.content)
    }

    // -----------------------------------------------------------------------
    // 10. Member list consistency
    // -----------------------------------------------------------------------

    @Test
    fun testMemberListConsistency_AfterAdditions() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        assertEquals(1, alice.members().size)

        val bobBundle = createStandaloneKeyPackage("bob")
        alice.addMember(bobBundle.keyPackage.toTlsBytes())
        assertEquals(2, alice.members().size)

        val carolBundle = createStandaloneKeyPackage("carol")
        alice.addMember(carolBundle.keyPackage.toTlsBytes())
        assertEquals(3, alice.members().size)

        // All members should have valid LeafNodes
        for ((_, leafNode) in alice.members()) {
            assertEquals(32, leafNode.encryptionKey.size)
            assertEquals(32, leafNode.signatureKey.size)
            assertTrue(leafNode.signature.isNotEmpty())
        }
    }
}
